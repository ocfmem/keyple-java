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
package org.eclipse.keyple.example.common.calypso.pc.transaction;


import org.eclipse.keyple.calypso.command.po.parser.AppendRecordRespPars;
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.transaction.*;
import org.eclipse.keyple.core.selection.*;
import org.eclipse.keyple.core.seproxy.*;
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsRequest;
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsResponse;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.eclipse.keyple.example.common.calypso.postructure.CalypsoClassicInfo;
import org.eclipse.keyple.example.common.generic.AbstractReaderObserverEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.profiler.Profiler;

/**
 * This Calypso demonstration code consists in:
 *
 * <ol>
 * <li>Setting up a two-reader configuration and adding an observer method ({@link #update update})
 * <li>Starting a card operation when a PO presence is notified
 * ({@link #processSeMatch(AbstractDefaultSelectionsRequest)} operateSeTransaction})
 * <li>Opening a logical channel with the SAM (C1 SAM is expected) see
 * ({@link CalypsoClassicInfo#SAM_C1_ATR_REGEX SAM_C1_ATR_REGEX})
 * <li>Attempting to open a logical channel with the PO with 3 options:
 * <ul>
 * <li>Selecting with a fake AID (1)
 * <li>Selecting with the Calypso AID and reading the event log file
 * <li>Selecting with a fake AID (2)
 * </ul>
 * <li>Display {@link AbstractDefaultSelectionsResponse} data
 * <li>If the Calypso selection succeeded, do a Calypso transaction
 * ({doCalypsoReadWriteTransaction(PoTransaction, ApduResponse, boolean)}
 * doCalypsoReadWriteTransaction}).
 * </ol>
 *
 * <p>
 * The Calypso transactions demonstrated here shows the Keyple API in use with Calypso SE (PO and
 * SAM).
 *
 * <p>
 * Read the doc of each methods for further details.
 */
@SuppressWarnings("unused")
public class CalypsoClassicTransactionEngine extends AbstractReaderObserverEngine {
    private static Logger logger = LoggerFactory.getLogger(CalypsoClassicTransactionEngine.class);

    /* define the SAM parameters to provide when creating PoTransaction */
    private final SecuritySettings securitySettings = new SecuritySettings();
    private SeReader poReader, samReader;
    private SamResource samResource = null;

    private SeSelection seSelection;

    private boolean samChannelOpen;

    /* Constructor */
    public CalypsoClassicTransactionEngine() {
        super();
        this.samChannelOpen = false;
    }

    /* Assign readers to the transaction engine */
    public void setReaders(SeReader poReader, SeReader samReader) {
        this.poReader = poReader;
        this.samReader = samReader;
    }

    /**
     * Do a Calypso transaction
     * <p>
     * Nominal case (the previous transaction was ratified):
     * <ul>
     * <li>Process opening
     * <ul>
     * <li>Reading the event log file</li>
     * <li>Reading the contract list</li>
     * </ul>
     * </li>
     * <li>Process PO commands
     * <ul>
     * <li>Reading the 4 contracts</li>
     * </ul>
     * </li>
     * <li>Process closing
     * <ul>
     * <li>A new record is appended to the event log file</li>
     * <li>The session is closed in CONTACTLESS_MODE (a ratification command is sent)</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * Alternate case (the previous transaction was not ratified):
     * <ul>
     * <li>Process opening
     * <ul>
     * <li>Reading the event log file</li>
     * <li>Reading the contract list</li>
     * </ul>
     * </li>
     * <li>Process closing
     * <ul>
     * <li>The session is closed in CONTACTLESS_MODE (a ratification command is sent)</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * The PO logical channel is kept open or closed according to the closeSeChannel flag
     *
     * @param poTransaction PoTransaction object
     * @param closeSeChannel flag to ask or not the channel closing at the end of the transaction
     * @throws KeypleReaderException reader exception (defined as public for purposes of javadoc)
     */
    public void doCalypsoReadWriteTransaction(PoTransaction poTransaction, boolean closeSeChannel)
            throws KeypleReaderException {

        boolean poProcessStatus;

        /*
         * Read commands to execute during the opening step: EventLog, ContractList
         */

        /* prepare Event Log read record */
        int readEventLogParserIndex = poTransaction.prepareReadRecordsCmd(
                CalypsoClassicInfo.SFI_EventLog, ReadDataStructure.SINGLE_RECORD_DATA,
                CalypsoClassicInfo.RECORD_NUMBER_1, String.format("EventLog (SFI=%02X, recnbr=%d))",
                        CalypsoClassicInfo.SFI_EventLog, CalypsoClassicInfo.RECORD_NUMBER_1));


        /* prepare Contract List read record */
        int readContractListParserIndex = poTransaction.prepareReadRecordsCmd(
                CalypsoClassicInfo.SFI_ContractList, ReadDataStructure.SINGLE_RECORD_DATA,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                String.format("ContractList (SFI=%02X))", CalypsoClassicInfo.SFI_ContractList));

        if (logger.isInfoEnabled()) {
            logger.info(
                    "========= PO Calypso session ======= Opening ============================");
        }

        /*
         * Open Session for the debit key - with reading of the first record of the cyclic EF of
         * Environment and Holder file
         */
        poProcessStatus = poTransaction.processOpening(PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT,
                CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1);

        logger.info("Parsing Read EventLog file: "
                + ((ReadRecordsRespPars) poTransaction.getResponseParser(readEventLogParserIndex))
                        .toString());

        logger.info("Parsing Read ContractList file: " + ((ReadRecordsRespPars) poTransaction
                .getResponseParser(readContractListParserIndex)).toString());

        if (!poTransaction.wasRatified()) {
            logger.info(
                    "========= Previous Secure Session was not ratified. =====================");

            /*
             * [------------------------------------]
             *
             * The previous Secure Session has not been ratified, so we simply close the Secure
             * Session.
             *
             * We would analyze here the event log read during the opening phase.
             *
             * [------------------------------------]
             */

            if (logger.isInfoEnabled()) {
                logger.info(
                        "========= PO Calypso session ======= Closing ============================");
            }

            /*
             * A ratification command will be sent (CONTACTLESS_MODE).
             */
            poProcessStatus = poTransaction.processClosing(ChannelControl.CLOSE_AFTER);

        } else {
            /*
             * [------------------------------------]
             *
             * Place to analyze the PO profile available in seResponse: Environment/Holder,
             * EventLog, ContractList.
             *
             * The information available allows the determination of the contract to be read.
             *
             * [------------------------------------]
             */

            if (logger.isInfoEnabled()) {
                logger.info(
                        "========= PO Calypso session ======= Processing of PO commands =======================");
            }

            /* Read contract command (we assume we have determine Contract #1 to be read. */
            /* prepare Contract #1 read record */
            int readContractsParserIndex = poTransaction.prepareReadRecordsCmd(
                    CalypsoClassicInfo.SFI_Contracts, ReadDataStructure.MULTIPLE_RECORD_DATA,
                    CalypsoClassicInfo.RECORD_NUMBER_1,
                    String.format("Contracts (SFI=%02X, recnbr=%d)",
                            CalypsoClassicInfo.SFI_Contracts, CalypsoClassicInfo.RECORD_NUMBER_1));

            /* proceed with the sending of commands, don't close the channel */
            poProcessStatus = poTransaction.processPoCommandsInSession();

            logger.info("Parsing Read Contract file: " + ((ReadRecordsRespPars) poTransaction
                    .getResponseParser(readContractsParserIndex)).toString());

            if (logger.isInfoEnabled()) {
                logger.info(
                        "========= PO Calypso session ======= Closing ============================");
            }

            /*
             * [------------------------------------]
             *
             * Place to analyze the Contract (in seResponse).
             *
             * The content of the contract will grant or not access.
             *
             * In any case, a new record will be added to the EventLog.
             *
             * [------------------------------------]
             */

            /* prepare Event Log append record */
            int appendEventLogParserIndex =
                    poTransaction.prepareAppendRecordCmd(CalypsoClassicInfo.SFI_EventLog,
                            ByteArrayUtil.fromHex(CalypsoClassicInfo.eventLog_dataFill),
                            String.format("EventLog (SFI=%02X)", CalypsoClassicInfo.SFI_EventLog));

            /*
             * A ratification command will be sent (CONTACTLESS_MODE).
             */
            poProcessStatus = poTransaction.processClosing(ChannelControl.CLOSE_AFTER);

            logger.info("Parsing Append EventLog file: " + ((AppendRecordRespPars) poTransaction
                    .getResponseParser(appendEventLogParserIndex)).toString());
        }

        if (poTransaction.isSuccessful()) {
            if (logger.isInfoEnabled()) {
                logger.info(
                        "========= PO Calypso session ======= SUCCESS !!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        } else {
            logger.error(
                    "========= PO Calypso session ======= ERROR !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    public AbstractDefaultSelectionsRequest preparePoSelection() {
        /*
         * Initialize the selection process
         */
        seSelection = new SeSelection();

        /* operate multiple PO selections */
        String poFakeAid1 = "AABBCCDDEE"; // fake AID 1
        String poFakeAid2 = "EEDDCCBBAA"; // fake AID 2

        /*
         * Add selection case 1: Fake AID1, protocol ISO, target rev 3
         */
        seSelection.prepareSelection(
                new PoSelectionRequest(new PoSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                        new PoSelector.PoAidSelector(new SeSelector.AidSelector.IsoAid(poFakeAid1),
                                PoSelector.InvalidatedPo.REJECT),
                        "Selector with fake AID1")));

        /*
         * Add selection case 2: Calypso application, protocol ISO, target rev 2 or 3
         *
         * addition of read commands to execute following the selection
         */
        PoSelectionRequest poSelectionRequestCalypsoAid =
                new PoSelectionRequest(new PoSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                        new PoSelector.PoAidSelector(
                                new SeSelector.AidSelector.IsoAid(CalypsoClassicInfo.AID),
                                PoSelector.InvalidatedPo.ACCEPT),
                        "Calypso selector"));

        poSelectionRequestCalypsoAid.prepareReadRecordsCmd(CalypsoClassicInfo.SFI_EventLog,
                ReadDataStructure.SINGLE_RECORD_DATA, CalypsoClassicInfo.RECORD_NUMBER_1,
                "EventLog (selection step)");

        seSelection.prepareSelection(poSelectionRequestCalypsoAid);

        /*
         * Add selection case 3: Fake AID2, unspecified protocol, target rev 2 or 3
         */
        seSelection.prepareSelection(
                new PoSelectionRequest(new PoSelector(SeCommonProtocols.PROTOCOL_B_PRIME, null,
                        new PoSelector.PoAidSelector(new SeSelector.AidSelector.IsoAid(poFakeAid2),
                                PoSelector.InvalidatedPo.REJECT),
                        "Selector with fake AID2")));

        /*
         * Add selection case 4: ATR selection, rev 1 atrregex
         */
        seSelection.prepareSelection(
                new PoSelectionRequest(new PoSelector(SeCommonProtocols.PROTOCOL_B_PRIME,
                        new PoSelector.PoAtrFilter(CalypsoClassicInfo.ATR_REV1_REGEX), null,
                        "Selector with fake AID2")));

        return seSelection.getSelectionOperation();
    }

    /**
     * Do the PO selection and possibly go on with Calypso transactions.
     */
    @Override
    public void processSeMatch(AbstractDefaultSelectionsResponse defaultSelectionsResponse) {
        CalypsoPo calypsoPo =
                (CalypsoPo) seSelection.processDefaultSelection(defaultSelectionsResponse)
                        .getActiveSelection().getMatchingSe();
        if (calypsoPo != null) {
            try {
                /* first time: check SAM */
                if (!this.samChannelOpen) {
                    /* the following method will throw an exception if the SAM is not available. */
                    samResource = CalypsoUtilities.checkSamAndOpenChannel(samReader);
                    this.samChannelOpen = true;
                }

                Profiler profiler = new Profiler("Entire transaction");

                /* Time measurement */
                profiler.start("Initial selection");

                profiler.start("Calypso1");

                PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, calypsoPo),
                        samResource, securitySettings);

                doCalypsoReadWriteTransaction(poTransaction, true);

                profiler.stop();
                logger.warn(System.getProperty("line.separator") + "{}", profiler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void processSeInserted() {
        System.out.println("Unexpected SE insertion event");
    }

    @Override
    public void processSeRemoved() {
        System.out.println("SE removal event");
    }

    @Override
    public void processUnexpectedSeRemoval() {
        System.out.println("Unexpected SE removal event");
    }
}
