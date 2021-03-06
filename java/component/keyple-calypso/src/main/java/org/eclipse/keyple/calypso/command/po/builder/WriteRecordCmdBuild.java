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
package org.eclipse.keyple.calypso.command.po.builder;


import org.eclipse.keyple.calypso.command.PoClass;
import org.eclipse.keyple.calypso.command.po.AbstractPoCommandBuilder;
import org.eclipse.keyple.calypso.command.po.CalypsoPoCommand;
import org.eclipse.keyple.calypso.command.po.parser.WriteRecordRespPars;
import org.eclipse.keyple.core.seproxy.message.ApduResponse;

/**
 * The Class WriteRecordCmdBuild. This class provides the dedicated constructor to build the Write
 * Record APDU command.
 *
 */
public final class WriteRecordCmdBuild extends AbstractPoCommandBuilder<WriteRecordRespPars> {

    /** The command. */
    private static final CalypsoPoCommand command = CalypsoPoCommand.WRITE_RECORD;

    /* Construction arguments */
    private final int sfi;
    private final int recordNumber;
    private final byte[] data;

    /**
     * Instantiates a new WriteRecordCmdBuild.
     *
     * @param poClass indicates which CLA byte should be used for the Apdu
     * @param sfi the sfi to select
     * @param recordNumber the record number to write
     * @param newRecordData the new record data to write
     * @throws IllegalArgumentException - if record number is &lt; 1
     * @throws IllegalArgumentException - if the request is inconsistent
     */
    public WriteRecordCmdBuild(PoClass poClass, byte sfi, byte recordNumber, byte[] newRecordData) {
        super(command, null);
        if (recordNumber < 1) {
            throw new IllegalArgumentException("Bad record number (< 1)");
        }

        // TODO complete argument checking
        byte cla = poClass.getValue();
        this.sfi = sfi;
        this.recordNumber = recordNumber;
        this.data = newRecordData;

        byte p2 = (sfi == 0) ? (byte) 0x04 : (byte) ((byte) (sfi * 8) + 4);

        this.request = setApduRequest(cla, command, recordNumber, p2, newRecordData, null);

        if (logger.isDebugEnabled()) {
            String extraInfo = String.format("SFI=%02X, REC=%d", sfi, recordNumber);
            this.addSubName(extraInfo);
        }
    }

    @Override
    public WriteRecordRespPars createResponseParser(ApduResponse apduResponse) {
        return new WriteRecordRespPars(apduResponse, this);
    }

    /**
     * This command can modify the contents of the PO in session and therefore uses the session
     * buffer.
     *
     * @return true
     */
    @Override
    public boolean isSessionBufferUsed() {
        return true;
    }


    /**
     * @return the SFI of the accessed file
     */
    public int getSfi() {
        return sfi;
    }

    /**
     * @return the number of the accessed record
     */
    public int getRecordNumber() {
        return recordNumber;
    }

    /**
     * @return the data sent to the PO
     */
    public byte[] getData() {
        return data;
    }
}
