/********************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.calypso.command.po.exception;

import org.eclipse.keyple.calypso.command.po.CalypsoPoCommand;

/**
 * The exception {@code CalypsoPoTerminatedException} indicates that the number of transactions
 * authorized by the PO has reached its limit.<br>
 * This may occur, for example, when requesting an open secure session.
 */
public final class CalypsoPoTerminatedException extends CalypsoPoCommandException {

    /**
     * @param message the message to identify the exception context
     * @param command the Calypso PO command
     * @param statusCode the status code
     */
    public CalypsoPoTerminatedException(String message, CalypsoPoCommand command,
            Integer statusCode) {
        super(message, command, statusCode);
    }
}
