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
package org.eclipse.keyple.integration.example.pc.calypso;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.command.sam.SamRevision;
import org.eclipse.keyple.calypso.transaction.*;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.selection.SelectionsResult;
import org.eclipse.keyple.core.seproxy.*;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.event.ReaderEvent;
import org.eclipse.keyple.core.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.core.seproxy.message.*;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.integration.IntegrationUtils;
import org.eclipse.keyple.integration.calypso.PoFileStructureInfo;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactory;
import org.eclipse.keyple.plugin.pcsc.PcscProtocolSetting;
import org.eclipse.keyple.plugin.pcsc.PcscReader;

@SuppressWarnings("PMD.VariableNamingConventions")
public class Demo_ValidationTransaction implements ObservableReader.ReaderObserver {

    private SeReader poReader;
    protected SamResource samResource;

    @Override
    public void update(ReaderEvent event) {
        switch (event.getEventType()) {
            case SE_INSERTED:
                System.out.println(
                        "\n==================================================================================");
                System.out.println("Found a Calypso PO! Validating...\n");
                detectAndHandlePO();
                /*
                 * informs the underlying layer of the end of the SE processing, in order to manage
                 * the removal sequence
                 */
                try {
                    ((ObservableReader) SeProxyService.getInstance()
                            .getPlugin(event.getPluginName()).getReader(event.getReaderName()))
                                    .notifySeProcessed();
                } catch (KeypleReaderNotFoundException e) {
                    e.printStackTrace();
                } catch (KeyplePluginNotFoundException e) {
                    e.printStackTrace();
                }
                break;
            case SE_REMOVED:
                System.out.println("\nWaiting for new Calypso PO...");
                break;
            default:
                System.out.println("IO Error");
        }
    }

    public static byte[] longToBytes(long lg) {
        byte[] result = new byte[8];
        long l = lg;
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    public static int getCounterValueFromByteArray(byte[] inByteArray, int inCounterIndex) {
        int counterValue = 0;

        for (int i = 0; i < 3; i++) {
            counterValue <<= 8;
            counterValue |= (inByteArray[i + (3 * (inCounterIndex - 1))] & 0xFF);
        }

        return counterValue;
    }

    public static byte[] getByteArrayFromCounterValue(int inCounterValue) {

        byte[] result = new byte[3];

        int counter = inCounterValue;
        for (int i = 2; i >= 0; i--) {
            result[i] = (byte) (inCounterValue & 0xFF);
            counter >>= 8;
        }

        return result;
    }

    // Not optimized for online/remote operation
    private void validateAuditC0(PoTransaction poTransaction) throws KeypleReaderException {

        byte eventSfi = 0x08;
        byte contractListSfi = 0x1E;
        byte environmentSfi = 0x07;


        int readEventParserIndex = poTransaction.prepareReadRecordsCmd(eventSfi,
                ReadDataStructure.SINGLE_RECORD_DATA, (byte) 0x01, "Event");
        int readContractListParserIndex = poTransaction.prepareReadRecordsCmd(contractListSfi,
                ReadDataStructure.SINGLE_RECORD_DATA, (byte) 0x01, "ContractList");

        // Open Session with debit key #3 and reading the Environment at SFI 07h
        // Files to read during the beginning of the session: Event (SFI 0x08) and ContractList (SFI
        // 0x1E)
        poTransaction.processOpening(PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT, environmentSfi, (byte) 0x01);

        byte contractIndex =
                ((ReadRecordsRespPars) poTransaction.getResponseParser(readContractListParserIndex))
                        .getRecords().get(1)[0];
        byte[] eventTimestampData = Arrays.copyOfRange(
                ((ReadRecordsRespPars) poTransaction.getResponseParser(readEventParserIndex))
                        .getRecords().get(1),
                1, (Long.SIZE / Byte.SIZE) + 1);

        String timeStampString = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                .format(new Date(bytesToLong(eventTimestampData)));

        System.out.println(
                "\t------------------------------------------------------------------------------");
        String nameInPO = new String(poTransaction.getOpenRecordDataRead());
        System.out.println("\tName in PO:: " + nameInPO);
        System.out.println(
                "\t------------------------------------------------------------------------------");
        System.out.println("\tPrevious Event Information");
        System.out.println(
                "\t- Index of Validated Contract:: " + (contractIndex == 0 ? 4 : contractIndex));
        System.out.println("\t- Contract Type:: Season Pass");
        System.out.println("\t- Event DateTime:: " + timeStampString);
        System.out.println(
                "\t------------------------------------------------------------------------------\n");

        int readContractParserIndex = poTransaction.prepareReadRecordsCmd((byte) 0x29,
                ReadDataStructure.SINGLE_RECORD_DATA, (byte) (contractIndex + 1), (byte) 0x1D,
                "Contract");

        poTransaction.processPoCommandsInSession();

        System.out
                .println("Reading contract #" + (contractIndex + 1) + " for current validation...");

        /*
         * System.out.println("Contract#" + (contractIndex+1) + ": " +
         * ByteArrayUtil.toHex(dataReadInSession.getApduResponses().get(0).getDataOut()) +
         * ", SW1SW2: " +
         * Integer.toHexString(dataReadInSession.getApduResponses().get(0).getStatusCode() &
         * 0xFFFF));
         */

        byte[] newEventData = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] newContractListData =
                new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        if (contractIndex < 3) {
            newContractListData[0] = (byte) (contractIndex + 1);
        }

        newEventData[0] = (byte) (contractIndex + 1);

        byte[] dateToInsert = longToBytes(new Date().getTime());
        System.arraycopy(dateToInsert, 0, newEventData, 1, (Long.SIZE / Byte.SIZE));

        int updateContractListParserIndex = poTransaction.prepareUpdateRecordCmd(contractListSfi,
                (byte) 0x01, newContractListData, "ContractList");
        int appendEventParsIndex =
                poTransaction.prepareAppendRecordCmd(eventSfi, newEventData, "Event");

        poTransaction.processPoCommandsInSession();

        poTransaction.processClosing(ChannelControl.KEEP_OPEN);

        System.out.println("\nValidation Successful!");
        System.out.println(
                "==================================================================================");
    }


    // Optimised for online/remote operation
    private void validateClap(CalypsoPo detectedPO) throws KeypleReaderException {

        byte eventSfi = 0x08;
        byte countersSfi = 0x1B;
        byte environmentSfi = 0x14;
        byte contractsSfi = 0x29;

        SeResponse dataReadInSession;
        PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, detectedPO),
                samResource, new SecuritySettings());

        int readEventParserIndex = poTransaction.prepareReadRecordsCmd(eventSfi,
                ReadDataStructure.SINGLE_RECORD_DATA, (byte) 0x01, "Event");
        int readCountersParserIndex = poTransaction.prepareReadRecordsCmd(countersSfi,
                ReadDataStructure.SINGLE_COUNTER, (byte) 0x01, "Counters");
        poTransaction.prepareReadRecordsCmd(contractsSfi, ReadDataStructure.MULTIPLE_RECORD_DATA,
                (byte) 0x01, "Contracts");

        // Open Session with debit key #3 and reading the Environment at SFI 07h
        // Files to read during the beginning of the session: Event (SFI 0x08), Counters (SFI 0x1B)
        // and all records of the Contracts (SFI 0x29)
        poTransaction.processOpening(PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT, environmentSfi, (byte) 0x01);

        byte[] eventTimestampData = Arrays.copyOfRange(
                ((ReadRecordsRespPars) poTransaction.getResponseParser(readEventParserIndex))
                        .getRecords().get(1),
                1, (Long.SIZE / Byte.SIZE) + 1);

        String timeStampString = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                .format(new Date(bytesToLong(eventTimestampData)));

        int counterValue =
                ((ReadRecordsRespPars) poTransaction.getResponseParser(readEventParserIndex))
                        .getCounters().get(0);

        System.out.println(
                "\t------------------------------------------------------------------------------");
        String nameInPO = new String(poTransaction.getOpenRecordDataRead());
        System.out.println("\tName in PO:: " + nameInPO);
        System.out.println(
                "\t------------------------------------------------------------------------------");
        System.out.println("\tPrevious Event Information");
        System.out.println("\t- Index of Validated Contract:: 1");
        System.out.println("\t- Contract Type:: MultiTrip Ticket");
        System.out.println("\t- Counter Value:: " + counterValue);
        System.out.println("\t- Event DateTime:: " + timeStampString);
        System.out.println(
                "\t------------------------------------------------------------------------------\n");

        System.out.println("All contracts read during the beginning of the current transaction...");

        // Perform automatic top-up when the value is 0 by closing the current session and opening a
        // new one with a loading key
        if (counterValue == 0) {

            System.out.println("No value present in the card. Initiating auto top-up...");

            poTransaction.processClosing(ChannelControl.KEEP_OPEN);

            poTransaction = new PoTransaction(new PoResource(poReader, detectedPO), samResource,
                    new SecuritySettings());

            poTransaction.processOpening(PoTransaction.ModificationMode.ATOMIC,
                    PoTransaction.SessionAccessLevel.SESSION_LVL_LOAD, (byte) 0x00, (byte) 0x00);

            byte[] newCounterData = new byte[] {0x00, 0x00, 0x05, 0x00, 0x00, 0x00};

            poTransaction.prepareUpdateRecordCmd(countersSfi, (byte) 0x01, newCounterData,
                    "Counter");
            counterValue = 5;
        }

        /*
         * System.out.println("Contract#" + (contractIndex+1) + ": " +
         * ByteArrayUtil.toHex(dataReadInSession.getApduResponses().get(0).getDataOut()) +
         * ", SW1SW2: " +
         * Integer.toHexString(dataReadInSession.getApduResponses().get(0).getStatusCode() &
         * 0xFFFF));
         */

        byte[] newEventData = new byte[] {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] dateToInsert = longToBytes(new Date().getTime());
        System.arraycopy(dateToInsert, 0, newEventData, 1, (Long.SIZE / Byte.SIZE));

        poTransaction.prepareAppendRecordCmd(eventSfi, newEventData, "Event");

        poTransaction.prepareDecreaseCmd(countersSfi, (byte) 0x01, 1, "Counter decval=1");

        poTransaction.processPoCommandsInSession();

        poTransaction.processClosing(ChannelControl.KEEP_OPEN);

        System.out.println("\nValidation Successful!");
        System.out.println(
                "==================================================================================");
    }


    private void detectAndHandlePO() {

        try {
            // operate PO multiselection
            String poAuditC0Aid = "315449432E4943414C54"; // AID of the PO with Audit C0 profile
            String clapAid = "315449432E494341D62010029101"; // AID of the CLAP product being tested
            String cdLightAid = "315449432E494341"; // AID of the Rev2.4 PO emulating CDLight

            SeSelection seSelection = new SeSelection();

            // Add Audit C0 AID to the list
            int auditC0SeIndex = seSelection.prepareSelection(new PoSelectionRequest(new PoSelector(
                    SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                    new PoSelector.PoAidSelector(
                            new SeSelector.AidSelector.IsoAid(PoFileStructureInfo.poAuditC0Aid),
                            null),
                    "Audit C0")));

            // Add CLAP AID to the list
            int clapSeIndex = seSelection.prepareSelection(new PoSelectionRequest(new PoSelector(
                    SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                    new PoSelector.PoAidSelector(
                            new SeSelector.AidSelector.IsoAid(PoFileStructureInfo.clapAid), null),
                    "CLAP")));

            // Add cdLight AID to the list
            int cdLightSeIndex = seSelection.prepareSelection(new PoSelectionRequest(new PoSelector(
                    SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                    new PoSelector.PoAidSelector(
                            new SeSelector.AidSelector.IsoAid(PoFileStructureInfo.cdLightAid),
                            null),
                    "CDLight")));

            SelectionsResult selectionsResult = seSelection.processExplicitSelection(poReader);

            if (selectionsResult == null) {
                throw new IllegalArgumentException("No recognizable PO detected.");
            }


            // Depending on the PO detected perform either a Season Pass validation or a MultiTrip
            // validation
            int matchingSelectionIndex = selectionsResult.getActiveSelection().getSelectionIndex();

            if (matchingSelectionIndex == auditC0SeIndex) {
                CalypsoPo auditC0Se = (CalypsoPo) selectionsResult
                        .getMatchingSelection(auditC0SeIndex).getMatchingSe();

                PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, auditC0Se),
                        samResource, new SecuritySettings());
                validateAuditC0(poTransaction);

            } else if (matchingSelectionIndex == clapSeIndex) {
                CalypsoPo clapSe = (CalypsoPo) selectionsResult.getMatchingSelection(clapSeIndex)
                        .getMatchingSe();

                PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, clapSe),
                        samResource, new SecuritySettings());
                validateClap(clapSe);

            } else if (matchingSelectionIndex == cdLightSeIndex) {

                CalypsoPo cdLightSe = (CalypsoPo) selectionsResult
                        .getMatchingSelection(cdLightSeIndex).getMatchingSe();

                PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, cdLightSe),
                        samResource, new SecuritySettings());
                validateAuditC0(poTransaction);

            } else {
                System.out.println("No recognizable PO detected.");
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static final Object waitForEnd = new Object();

    public static void main(String[] args)
            throws IOException, InterruptedException, KeypleBaseException {

        SeProxyService seProxyService = SeProxyService.getInstance();

        /* Assign PcscPlugin to the SeProxyService */
        seProxyService.registerPlugin(new PcscPluginFactory());

        SeReader poReader =
                IntegrationUtils.getReader(seProxyService, IntegrationUtils.PO_READER_NAME_REGEX);

        poReader.addSeProtocolSetting(SeCommonProtocols.PROTOCOL_ISO14443_4,
                PcscProtocolSetting.PCSC_PROTOCOL_SETTING
                        .get(SeCommonProtocols.PROTOCOL_ISO14443_4));

        SeReader samReader =
                IntegrationUtils.getReader(seProxyService, IntegrationUtils.SAM_READER_NAME_REGEX);

        samReader.addSeProtocolSetting(SeCommonProtocols.PROTOCOL_ISO7816_3,
                PcscProtocolSetting.PCSC_PROTOCOL_SETTING
                        .get(SeCommonProtocols.PROTOCOL_ISO7816_3));

        if (poReader == samReader || poReader == null || samReader == null) {
            throw new IllegalStateException("Bad PO/SAM setup");
        }

        System.out.println(
                "\n==================================================================================");
        System.out.println("PO Reader  : " + poReader.getName());
        System.out.println("SAM Reader : " + samReader.getName());
        System.out.println(
                "==================================================================================");

        poReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T1);
        samReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T0);

        // provide the reader with the protocol settings
        poReader.addSeProtocolSetting(SeCommonProtocols.PROTOCOL_ISO14443_4,
                PcscProtocolSetting.PCSC_PROTOCOL_SETTING
                        .get(SeCommonProtocols.PROTOCOL_ISO14443_4));

        final String SAM_ATR_REGEX = "3B3F9600805A[0-9a-fA-F]{2}80[0-9a-fA-F]{16}829000";

        SeSelection samSelection = new SeSelection();

        SamSelectionRequest samSelectionRequest =
                new SamSelectionRequest(new SamSelector(SamRevision.C1, null, "SAM Selection"));

        /* Prepare selector, ignore AbstractMatchingSe here */
        samSelection.prepareSelection(samSelectionRequest);

        SamResource samResource;

        try {
            SelectionsResult selectionsResult = samSelection.processExplicitSelection(samReader);
            if (!selectionsResult.hasActiveSelection()) {
                System.out.println("Unable to open a logical channel for SAM!");
                throw new IllegalStateException("SAM channel opening failure");
            }
            samResource = new SamResource(samReader,
                    (CalypsoSam) selectionsResult.getActiveSelection().getMatchingSe());
        } catch (KeypleReaderException e) {
            throw new IllegalStateException("Reader exception: " + e.getMessage());
        }

        // Setting up ourselves as an observer
        Demo_ValidationTransaction observer = new Demo_ValidationTransaction();
        observer.poReader = poReader;
        observer.samResource = samResource;


        System.out.println("\nReady for PO presentation!");

        // Set terminal as Observer of the first reader
        ((ObservableReader) poReader).addObserver(observer);
        // start detection in repeating mode
        ((ObservableReader) poReader).startSeDetection(ObservableReader.PollingMode.REPEATING);
        synchronized (waitForEnd) {
            waitForEnd.wait();
        }
    }
}
