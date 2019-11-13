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
package org.eclipse.keyple.core.seproxy.exception;

public class KeyplePluginInstanciationException extends KeypleBaseException {


    public KeyplePluginInstanciationException(String message) {
        super(message);
    }

    public KeyplePluginInstanciationException(String message, Throwable cause) {
        super(message, cause);
    }
}
