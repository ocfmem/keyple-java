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
package org.eclipse.keyple.core.seproxy.protocol;


public enum Protocol implements SeProtocol {

    // Unspecified protocol
    ANY("Any protocol");

    /** The protocol name. */
    private String name;

    Protocol(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}