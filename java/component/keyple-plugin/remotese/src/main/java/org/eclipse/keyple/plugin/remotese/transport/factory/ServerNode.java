/********************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.plugin.remotese.transport.factory;


import org.eclipse.keyple.plugin.remotese.transport.DtoNode;

/**
 * Server type of a DtoNode, start and waits for clients to connect
 */
public interface ServerNode extends DtoNode {

    /**
     * Start the server and listen for incoming connections
     */
    void start();

}
