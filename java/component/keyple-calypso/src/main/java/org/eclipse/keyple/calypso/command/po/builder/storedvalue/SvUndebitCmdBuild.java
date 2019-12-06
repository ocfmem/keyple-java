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
package org.eclipse.keyple.calypso.command.po.builder.storedvalue;


import org.eclipse.keyple.calypso.command.PoClass;
import org.eclipse.keyple.calypso.command.po.*;
import org.eclipse.keyple.calypso.command.po.parser.storedvalue.SvUndebitRespPars;
import org.eclipse.keyple.core.seproxy.message.ApduResponse;

/**
 * The Class SvUndebitCmdBuild. This class provides the dedicated constructor to build the SV
 * Undebit command.
 */
public final class SvUndebitCmdBuild extends AbstractPoCommandBuilder<SvUndebitRespPars>
        implements PoSendableInSession, PoModificationCommand {

    /** The command. */
    private static final CalypsoPoCommands command = CalypsoPoCommands.SV_UNDEBIT;

    /**
     * Instantiates a new SvUndebitCmdBuild.
     *
     * @param poClass indicates which CLA byte should be used for the Apdu
     * @param poRevision the PO revision
     * @param amount amount to undebit (positive integer from 0 to 32767)
     * @param date debit date (not checked by the PO)
     * @param time debit time (not checked by the PO)
     * @param challenge challenge from the debit SAM
     * @param KVC debit key KVC (not checked by the PO)
     * @param samId debit SAM serial number (not checked by the PO)
     * @param samTNum debit SAM transaction number (not checked by the PO)
     * @param signatureHi MSB of the purchase signature generated by the SAM
     * @param extraInfo extra information included in the logs (can be null or empty)
     * @throws IllegalArgumentException - if the command is inconsistent
     */
    public SvUndebitCmdBuild(PoClass poClass, PoRevision poRevision, int amount, byte[] date,
            byte[] time, byte[] challenge, byte KVC, byte[] samId, byte[] samTNum,
            byte[] signatureHi, String extraInfo) {
        super(command, null);

        if (amount < 0 || amount > 32767) {
            throw new IllegalArgumentException(
                    "Amount is outside allowed boundaries (0 <= amount <= 32767)");
        }

        if ((poRevision == PoRevision.REV3_2 && signatureHi.length != 10)
                || (poRevision != PoRevision.REV3_2 && signatureHi.length != 5)) {
            throw new IllegalArgumentException("Bad signture length.");
        }

        byte cla = poClass.getValue();
        byte p1 = challenge[0];
        byte p2 = challenge[1];

        // handle the dataIn size with signatureHi length (the only varying field)
        byte[] dataIn = new byte[15 + signatureHi.length];

        dataIn[0] = challenge[2];
        dataIn[1] = (byte) ((amount >> 8) & 0xFF);
        dataIn[2] = (byte) (amount & 0xFF);
        dataIn[3] = date[0];
        dataIn[4] = date[1];
        dataIn[5] = time[0];
        dataIn[6] = time[1];
        dataIn[7] = KVC;
        System.arraycopy(samId, 0, dataIn, 8, 4);
        System.arraycopy(samTNum, 0, dataIn, 12, 3);
        System.arraycopy(signatureHi, 0, dataIn, 15, signatureHi.length);

        this.request = setApduRequest(cla, command, p1, p2, dataIn, null);
        if (extraInfo != null) {
            this.addSubName(extraInfo);
        }
    }

    @Override
    public SvUndebitRespPars createResponseParser(ApduResponse apduResponse) {
        return new SvUndebitRespPars(apduResponse);
    }
}