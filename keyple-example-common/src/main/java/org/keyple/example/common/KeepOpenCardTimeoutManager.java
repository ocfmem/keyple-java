/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.keyple.example.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.keyple.calypso.commands.po.PoRevision;
import org.keyple.calypso.commands.po.builder.ReadRecordsCmdBuild;
import org.keyple.calypso.commands.po.builder.UpdateRecordCmdBuild;
import org.keyple.seproxy.ApduRequest;
import org.keyple.seproxy.ByteBufferUtils;
import org.keyple.seproxy.ProxyReader;
import org.keyple.seproxy.SeRequest;
import org.keyple.seproxy.SeRequestElement;
import org.keyple.seproxy.SeResponse;

/**
 * Set of @{@link SeRequest} to test NFC Plugin with keep Open Channel. Two sets
 * of @{@link SeRequest} are sent to the NFC smartcard with a timeout of 3 seconds.
 */
public class KeepOpenCardTimeoutManager extends AbstractLogicManager {


    private ProxyReader poReader;

    public void setPoReader(ProxyReader poReader) {
        this.poReader = poReader;
    }

    @Override
    public void run() {
        super.run();
        String poAid = "A000000291A000000191";
        String t2UsageRecord1_dataFill = "0102030405060708090A0B0C0D0E0F10"
                + "1112131415161718191A1B1C1D1E1F20" + "2122232425262728292A2B2C2D2E2F30";

        ReadRecordsCmdBuild poReadRecordCmd_T2Env = new ReadRecordsCmdBuild(PoRevision.REV3_1,
                (byte) 0x01, true, (byte) 0x14, (byte) 0x20);
        ReadRecordsCmdBuild poReadRecordCmd_T2Usage = new ReadRecordsCmdBuild(PoRevision.REV3_1,
                (byte) 0x01, true, (byte) 0x1A, (byte) 0x30);
        UpdateRecordCmdBuild poUpdateRecordCmd_T2UsageFill =
                new UpdateRecordCmdBuild(PoRevision.REV3_1, (byte) 0x01, (byte) 0x1A,
                        ByteBufferUtils.fromHex(t2UsageRecord1_dataFill));

        // Get PO ApduRequest List
        List<ApduRequest> poApduRequestList = Arrays.asList(poReadRecordCmd_T2Env.getApduRequest(),
                poReadRecordCmd_T2Usage.getApduRequest(),
                poUpdateRecordCmd_T2UsageFill.getApduRequest());

        SeRequestElement seRequestElement =
                new SeRequestElement(ByteBufferUtils.fromHex(poAid), poApduRequestList, true);
        List<SeRequestElement> seRequestElements = new ArrayList<SeRequestElement>();
        seRequestElement.setProtocolFlag("android.nfc.tech.IsoDep");
        seRequestElements.add(seRequestElement);


        SeRequest poRequest = new SeRequest(seRequestElements);
        try {

            System.out.println("Transmit 1st SE Request, keep channel open");
            SeResponse poResponse = poReader.transmit(poRequest);
            getTopic().post(new Event("Got a response", "poResponse", poResponse));

            System.out.println("Sleeping for 3 seconds");
            Thread.sleep(3000);
            System.out.println("Transmit 2nd SE Request, close channel");

            SeRequestElement seRequestElement2 =
                    new SeRequestElement(ByteBufferUtils.fromHex(poAid), poApduRequestList, false);
            List<SeRequestElement> seRequestElements2 = new ArrayList<SeRequestElement>();
            seRequestElement2.setProtocolFlag("android.nfc.tech.IsoDep");

            seRequestElements2.add(seRequestElement2);
            SeRequest poRequest2 = new SeRequest(seRequestElements2);

            SeResponse poResponse2 = poReader.transmit(poRequest2);
            getTopic().post(new Event("Got a 2nd response", "poResponse2", poResponse2));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
