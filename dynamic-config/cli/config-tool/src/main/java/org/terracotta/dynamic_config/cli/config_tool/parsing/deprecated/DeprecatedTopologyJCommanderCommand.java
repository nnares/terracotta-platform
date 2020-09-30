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
package org.terracotta.dynamic_config.cli.config_tool.parsing.deprecated;

import com.beust.jcommander.Parameter;
import org.terracotta.dynamic_config.cli.command.JCommanderCommand;
import org.terracotta.dynamic_config.cli.config_tool.converter.OperationType;
import org.terracotta.dynamic_config.cli.converter.InetSocketAddressConverter;

import java.net.InetSocketAddress;

public abstract class DeprecatedTopologyJCommanderCommand extends JCommanderCommand {
  @Parameter(names = {"-t"}, description = "Determine if the sources are nodes or stripes. Default: node", converter = OperationType.TypeConverter.class)
  protected OperationType operationType = OperationType.NODE;

  @Parameter(required = true, names = {"-d"}, description = "Destination stripe or cluster", converter = InetSocketAddressConverter.class)
  protected InetSocketAddress destinationAddress;

  @Parameter(names = {"-f"}, description = "Force the operation")
  protected boolean force;
}
