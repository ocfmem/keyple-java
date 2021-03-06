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
package org.eclipse.keyple.calypso.command.sam.builder.security;

import org.eclipse.keyple.calypso.command.sam.AbstractSamCommandBuilder;
import org.eclipse.keyple.calypso.command.sam.CalypsoSamCommand;
import org.eclipse.keyple.calypso.command.sam.SamRevision;
import org.eclipse.keyple.calypso.command.sam.parser.security.DigestCloseRespPars;
import org.eclipse.keyple.core.seproxy.message.ApduResponse;

/**
 * Builder for the SAM Digest Close APDU command.
 */
public class DigestCloseCmdBuild extends AbstractSamCommandBuilder<DigestCloseRespPars> {

    /** The command. */
    private static final CalypsoSamCommand command = CalypsoSamCommand.DIGEST_CLOSE;

    /**
     * Instantiates a new DigestCloseCmdBuild .
     *
     * @param revision of the SAM
     * @param expectedResponseLength the expected response length
     * @throws IllegalArgumentException - if the expected response length is wrong.
     */
    public DigestCloseCmdBuild(SamRevision revision, byte expectedResponseLength) {
        super(command, null);
        if (revision != null) {
            this.defaultRevision = revision;
        }
        if (expectedResponseLength != 0x04 && expectedResponseLength != 0x08) {
            throw new IllegalArgumentException(String
                    .format("Bad digest length! Expected 4 or 8, got %s", expectedResponseLength));
        }

        byte cla = this.defaultRevision.getClassByte();
        byte p1 = (byte) 0x00;
        byte p2 = (byte) 0x00;

        request = setApduRequest(cla, command, p1, p2, null, expectedResponseLength);
    }

    @Override
    public DigestCloseRespPars createResponseParser(ApduResponse apduResponse) {
        return new DigestCloseRespPars(apduResponse, this);
    }
}
