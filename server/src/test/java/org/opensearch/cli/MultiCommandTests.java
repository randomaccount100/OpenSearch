/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.cli;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSet;
import joptsimple.util.KeyValuePair;
import org.junit.Before;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class MultiCommandTests extends CommandTestCase {

    static class DummyMultiCommand extends MultiCommand {

        final AtomicBoolean closed = new AtomicBoolean();

        DummyMultiCommand() {
            super("A dummy multi command", () -> {});
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (this.closed.compareAndSet(false, true) == false) {
                throw new IllegalStateException("DummyMultiCommand already closed");
            }
        }
    }

    static class DummySubCommand extends Command {
        final boolean throwsExceptionOnClose;
        final AtomicBoolean closeCalled = new AtomicBoolean();

        DummySubCommand() {
            this(false);
        }

        DummySubCommand(final boolean throwsExceptionOnClose) {
            super("A dummy subcommand", () -> {
            });
            this.throwsExceptionOnClose = throwsExceptionOnClose;
        }

        @Override
        protected void execute(Terminal terminal, OptionSet options) throws Exception {
            terminal.println("Arguments: " + options.nonOptionArguments().toString());
        }

        @Override
        public void close() throws IOException {
            if (this.closeCalled.compareAndSet(false, true) == false) {
                throw new IllegalStateException("DummySubCommand already closed");
            }
            if (throwsExceptionOnClose) {
                throw new IOException("Error occurred while closing DummySubCommand");
            }
        }
    }

    static class DummySettingsSubCommand extends DummySubCommand {
        private final ArgumentAcceptingOptionSpec<KeyValuePair> settingOption;

        DummySettingsSubCommand() {
            super();
            this.settingOption = parser.accepts("E", "Configure a setting").withRequiredArg().ofType(KeyValuePair.class);
        }

        @Override
        protected void execute(Terminal terminal, OptionSet options) throws Exception {
            final List<KeyValuePair> values = this.settingOption.values(options);
            terminal.println("Settings: " + values);
            super.execute(terminal, options);
        }
    }

    private DummyMultiCommand multiCommand;

    @Before
    public void setupCommand() {
        multiCommand = new DummyMultiCommand();
    }

    @Override
    protected Command newCommand() {
        return multiCommand;
    }

    public void testNoCommandsConfigured() {
        IllegalStateException e = expectThrows(IllegalStateException.class, this::execute);
        assertEquals("No subcommands configured", e.getMessage());
    }

    public void testUnknownCommand() {
        multiCommand.subcommands.put("something", new DummySubCommand());
        UserException e = expectThrows(UserException.class, () -> execute("somethingelse"));
        assertEquals(ExitCodes.USAGE, e.exitCode);
        assertEquals("Unknown command [somethingelse]", e.getMessage());
    }

    public void testMissingCommand() {
        multiCommand.subcommands.put("command1", new DummySubCommand());
        UserException e = expectThrows(UserException.class, this::execute);
        assertEquals(ExitCodes.USAGE, e.exitCode);
        assertEquals("Missing command", e.getMessage());
    }

    public void testHelp() throws Exception {
        multiCommand.subcommands.put("command1", new DummySubCommand());
        multiCommand.subcommands.put("command2", new DummySubCommand());
        execute("-h");
        String output = terminal.getOutput();
        assertTrue(output, output.contains("command1"));
        assertTrue(output, output.contains("command2"));
    }

    /**
     * Check that if -E arguments are passed to the main command, then they are accepted
     * and passed on to the subcommand.
     */
    public void testSettingsOnMainCommand() throws Exception {
        multiCommand.subcommands.put("command1", new DummySettingsSubCommand());
        execute("-Esetting1=value1", "-Esetting2=value2", "command1", "otherArg");

        String output = terminal.getOutput();
        assertThat(output, containsString("Settings: [setting1=value1, setting2=value2]"));
        assertThat(output, containsString("Arguments: [otherArg]"));
    }

    public void testSubcommandHelp() throws Exception {
        multiCommand.subcommands.put("command1", new DummySubCommand());
        multiCommand.subcommands.put("command2", new DummySubCommand());
        execute("command2", "-h");
        String output = terminal.getOutput();
        assertFalse(output, output.contains("command1"));
        assertTrue(output, output.contains("A dummy subcommand"));
    }

    public void testSubcommandArguments() throws Exception {
        multiCommand.subcommands.put("command1", new DummySubCommand());
        execute("command1", "foo", "bar");
        String output = terminal.getOutput();
        assertFalse(output, output.contains("command1"));
        assertTrue(output, output.contains("Arguments: [foo, bar]"));
    }

    public void testClose() throws Exception {
        DummySubCommand subCommand1 = new DummySubCommand();
        DummySubCommand subCommand2 = new DummySubCommand();
        multiCommand.subcommands.put("command1", subCommand1);
        multiCommand.subcommands.put("command2", subCommand2);
        multiCommand.close();
        assertTrue("MultiCommand was not closed when close method is invoked", multiCommand.closed.get());
        assertTrue("SubCommand1 was not closed when close method is invoked", subCommand1.closeCalled.get());
        assertTrue("SubCommand2 was not closed when close method is invoked", subCommand2.closeCalled.get());
    }

    public void testCloseWhenSubCommandCloseThrowsException() throws Exception {
        final boolean command1Throws = randomBoolean();
        final boolean command2Throws = randomBoolean();
        final DummySubCommand subCommand1 = new DummySubCommand(command1Throws);
        final DummySubCommand subCommand2 = new DummySubCommand(command2Throws);
        multiCommand.subcommands.put("command1", subCommand1);
        multiCommand.subcommands.put("command2", subCommand2);
        if (command1Throws || command2Throws) {
            // verify exception is thrown, as well as other non failed sub-commands closed
            // properly.
            IOException ioe = expectThrows(IOException.class, multiCommand::close);
            assertEquals("Error occurred while closing DummySubCommand", ioe.getMessage());
            if (command1Throws && command2Throws) {
                assertEquals(1, ioe.getSuppressed().length);
                assertTrue("Missing suppressed exceptions", ioe.getSuppressed()[0] instanceof IOException);
                assertEquals("Error occurred while closing DummySubCommand", ioe.getSuppressed()[0].getMessage());
            }
        } else {
            multiCommand.close();
        }
        assertTrue("SubCommand1 was not closed when close method is invoked", subCommand1.closeCalled.get());
        assertTrue("SubCommand2 was not closed when close method is invoked", subCommand2.closeCalled.get());
    }

    // Tests for multicommand error logging

    static class ErrorHandlingMultiCommand extends MultiCommand {
        ErrorHandlingMultiCommand() {
            super("error catching", () -> {});
        }

        @Override
        protected boolean addShutdownHook() {
            return false;
        }
    }

    static class ErrorThrowingSubCommand extends Command {
        ErrorThrowingSubCommand() {
            super("error throwing", () -> {});
        }
        @Override
        protected void execute(Terminal terminal, OptionSet options) throws Exception {
            throw new UserException(1, "Dummy error");
        }

        @Override
        protected boolean addShutdownHook() {
            return false;
        }
    }

    public void testErrorDisplayedWithDefault() throws Exception {
        MockTerminal terminal = new MockTerminal();
        MultiCommand mc = new ErrorHandlingMultiCommand();
        mc.subcommands.put("throw", new ErrorThrowingSubCommand());
        mc.main(new String[]{"throw", "--silent"}, terminal);
        assertThat(terminal.getOutput(), is(emptyString()));
        assertThat(terminal.getErrorOutput(), equalTo("ERROR: Dummy error\n"));
    }

    public void testNullErrorMessageSuppressesErrorOutput() throws Exception {
        MockTerminal terminal = new MockTerminal();
        MultiCommand mc = new ErrorHandlingMultiCommand();
        mc.subcommands.put("throw", new ErrorThrowingSubCommand() {
            @Override
            protected void execute(Terminal terminal, OptionSet options) throws Exception {
                throw new UserException(1, null);
            }
        });
        mc.main(new String[]{"throw", "--silent"}, terminal);
        assertThat(terminal.getOutput(), is(emptyString()));
        assertThat(terminal.getErrorOutput(), is(emptyString()));
    }

}
