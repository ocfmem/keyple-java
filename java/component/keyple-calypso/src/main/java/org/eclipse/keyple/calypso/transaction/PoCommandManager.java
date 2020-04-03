/********************************************************************************
 * Copyright (c) 2019 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.calypso.transaction;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.calypso.command.po.AbstractPoUserCommandBuilder;
import org.eclipse.keyple.core.command.AbstractApduResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The PO command manager handles the PoCommand list updated by the "prepare" methods of
 * PoTransaction. It is used to keep builders between the time the commands are created and the time
 * their responses are parsed.
 * <p>
 * A flag (preparedCommandsProcessed) is used to manage the reset of the command list. It allows the
 * builders to be kept until the application creates a new list of commands.
 * <p>
 * This flag is set by calling the method notifyCommandsProcessed and reset when a new PoCommand is
 * added or when a attempt
 */
class PoCommandManager {
    /* logger */
    private static final Logger logger = LoggerFactory.getLogger(PoCommandManager.class);

    /** The list to contain the prepared commands and their parsers */
    private final List<PoCommand> poCommandList = new ArrayList<PoCommand>();
    /** The command index, incremented each time a command is added */
    private boolean preparedCommandsProcessed;

    PoCommandManager() {
        preparedCommandsProcessed = true;
    }

    /**
     * Add a regular command to the builders and parsers list.
     * <p>
     * Handle the clearing of the list if needed.
     *
     * @param commandBuilder the command builder
     * @return the index to retrieve the parser later
     */
    int addRegularCommand(AbstractPoUserCommandBuilder commandBuilder) {
        /**
         * Reset the list if the preparation of the command is done after a previous processing
         * notified by notifyCommandsProcessed.
         * <p>
         * However, the parsers have remained available until now.
         */
        if (preparedCommandsProcessed) {
            poCommandList.clear();
            preparedCommandsProcessed = false;
        }

        poCommandList.add(new PoCommand(commandBuilder));
        /* return the command index */
        return (poCommandList.size() - 1);
    }

    /**
     * Informs that the commands have been processed.
     * <p>
     * Just record the information. The initialization of the list of commands will be done only the
     * next time a command is added, this allows access to the parsers contained in the list..
     */
    void notifyCommandsProcessed() {
        preparedCommandsProcessed = true;
    }

    /**
     * @return the current PoCommand list
     */
    public List<PoCommand> getPoCommandList() {
        /* Clear the list if no command has been added since the last call to a process method. */
        if (preparedCommandsProcessed) {
            poCommandList.clear();
            preparedCommandsProcessed = false;
        }
        return poCommandList;
    }

    /**
     * Returns the parser positioned at the indicated index
     * 
     * @param commandIndex the index of the wanted parser
     * @return the parser
     */
    public AbstractApduResponseParser getResponseParser(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= poCommandList.size()) {
            throw new IllegalArgumentException(
                    String.format("Bad command index: index = %d, number of commands = %d",
                            commandIndex, poCommandList.size()));
        }
        return poCommandList.get(commandIndex).getResponseParser();
    }
}
