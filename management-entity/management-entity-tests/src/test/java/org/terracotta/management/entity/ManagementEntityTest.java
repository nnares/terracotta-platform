/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.management.entity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.terracotta.connection.Connection;
import org.terracotta.entity.ServiceProviderConfiguration;
import org.terracotta.exception.EntityAlreadyExistsException;
import org.terracotta.exception.EntityNotFoundException;
import org.terracotta.exception.EntityNotProvidedException;
import org.terracotta.exception.EntityVersionMismatchException;
import org.terracotta.management.entity.client.ManagementAgentEntityClientService;
import org.terracotta.management.entity.client.ManagementAgentService;
import org.terracotta.management.entity.server.ManagementAgentEntityServerService;
import org.terracotta.management.model.capabilities.Capability;
import org.terracotta.management.model.cluster.ClientIdentifier;
import org.terracotta.management.model.context.ContextContainer;
import org.terracotta.management.registry.AbstractManagementRegistry;
import org.terracotta.management.registry.ManagementRegistry;
import org.terracotta.management.service.monitoring.IMonitoringConsumer;
import org.terracotta.management.service.monitoring.MonitoringConsumerConfiguration;
import org.terracotta.management.service.monitoring.MonitoringServiceConfiguration;
import org.terracotta.management.service.monitoring.MonitoringServiceProvider;
import org.terracotta.passthrough.IClusterControl;
import org.terracotta.passthrough.PassthroughTestHelpers;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Mathieu Carbou
 */
@RunWith(JUnit4.class)
public class ManagementEntityTest {

  private static IMonitoringConsumer consumer;

  @Test
  public void test_expose() throws EntityNotProvidedException, EntityVersionMismatchException, EntityAlreadyExistsException, EntityNotFoundException, IOException, ExecutionException, InterruptedException {
    IClusterControl stripeControl = PassthroughTestHelpers.createActiveOnly(server -> {
      server.setBindPort(9510);
      server.setGroupPort(9610);
      server.setServerName("server-1");
      server.registerClientEntityService(new ManagementAgentEntityClientService());
      server.registerServerEntityService(new ManagementAgentEntityServerService());
      server.registerServiceProvider(new HackedMonitoringServiceProvider(), new MonitoringServiceConfiguration().setDebug(true));
    });

    ManagementRegistry registry = new AbstractManagementRegistry() {
      @Override
      public ContextContainer getContextContainer() {
        return new ContextContainer("cacheManagerName", "my-cm-name");
      }
    };
    registry.addManagementProvider(new MyManagementProvider());
    registry.register(new MyObject("myCacheManagerName", "myCacheName1"));
    registry.register(new MyObject("myCacheManagerName", "myCacheName2"));

    try (Connection connection = stripeControl.createConnectionToActive()) {

      ManagementAgentService managementAgent = new ManagementAgentService(connection);

      ClientIdentifier clientIdentifier = managementAgent.getClientIdentifier();
      System.out.println(clientIdentifier);
      assertEquals(Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]), clientIdentifier.getPid());
      assertEquals("UNKNOWN", clientIdentifier.getName());
      assertNotNull(clientIdentifier.getConnectionUid());

      managementAgent.setTags("EhcachePounder", "webapp-1", "app-server-node-1");
      managementAgent.setCapabilities(registry.getContextContainer(), registry.getCapabilities());

      Collection<String> names = consumer.getChildNamesForNode(new String[]{"management", "clients"}).get();
      assertEquals(1, names.size());
      assertEquals(clientIdentifier.getClientId(), names.iterator().next());

      names = consumer.getChildNamesForNode(new String[]{"management", "clients", clientIdentifier.getClientId()}).get();
      assertEquals(2, names.size());
      assertThat(names, hasItem("tags"));
      assertThat(names, hasItem("cacheManagerName:my-cm-name"));

      assertArrayEquals(
          new String[]{"EhcachePounder", "webapp-1", "app-server-node-1"},
          consumer.getValueForNode(new String[]{"management", "clients", clientIdentifier.getClientId()}, "tags", String[].class).get());

      Map<String, Object> children = consumer.getChildValuesForNode(new String[]{"management", "clients", clientIdentifier.getClientId()}, "cacheManagerName:my-cm-name").get();
      assertEquals(2, children.size());
      assertArrayEquals(registry.getCapabilities().toArray(new Capability[0]), (Capability[]) children.get("capabilities"));
      assertEquals(registry.getContextContainer(), children.get("contextContainer"));
    }
  }

  // to be able to access the IMonitoringConsumer interface outside Voltron
  public static class HackedMonitoringServiceProvider extends MonitoringServiceProvider {
    @Override
    public boolean initialize(ServiceProviderConfiguration configuration) {
      super.initialize(configuration);
      consumer = getService(0, new MonitoringConsumerConfiguration());
      return true;
    }
  }

}
