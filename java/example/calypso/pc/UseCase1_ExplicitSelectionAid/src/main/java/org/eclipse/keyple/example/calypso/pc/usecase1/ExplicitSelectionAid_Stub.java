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
package org.eclipse.keyple.example.calypso.pc.usecase1;



import static org.eclipse.keyple.calypso.transaction.PoSelector.*;
import org.eclipse.keyple.calypso.transaction.CalypsoPo;
import org.eclipse.keyple.calypso.transaction.ElementaryFile;
import org.eclipse.keyple.calypso.transaction.PoResource;
import org.eclipse.keyple.calypso.transaction.PoSelectionRequest;
import org.eclipse.keyple.calypso.transaction.PoSelector;
import org.eclipse.keyple.calypso.transaction.PoTransaction;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.seproxy.ChannelControl;
import org.eclipse.keyple.core.seproxy.ReaderPlugin;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.exception.KeypleException;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.eclipse.keyple.example.common.calypso.postructure.CalypsoClassicInfo;
import org.eclipse.keyple.example.common.calypso.stub.StubCalypsoClassic;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubPluginFactory;
import org.eclipse.keyple.plugin.stub.StubProtocolSetting;
import org.eclipse.keyple.plugin.stub.StubReader;
import org.eclipse.keyple.plugin.stub.StubSecureElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h1>Use Case ‘Calypso 1’ – Explicit Selection Aid (Stub)</h1>
 * <ul>
 * <li>
 * <h2>Scenario:</h2>
 * <ul>
 * <li>Check if a ISO 14443-4 SE is in the reader, select a Calypso PO, operate a simple Calypso PO
 * transaction (simple plain read, not involving a Calypso SAM).</li>
 * <li><code>
 Explicit Selection
 </code> means that it is the terminal application which start the SE processing.</li>
 * <li>PO messages:
 * <ul>
 * <li>A first SE message to select the application in the reader</li>
 * <li>A second SE message to operate the simple Calypso transaction</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * </ul>
 */
public class ExplicitSelectionAid_Stub {
    private static final Logger logger = LoggerFactory.getLogger(ExplicitSelectionAid_Stub.class);

    public static void main(String[] args) throws KeypleException {

        // Get the instance of the SeProxyService (Singleton pattern)
        SeProxyService seProxyService = SeProxyService.getInstance();

        final String STUB_PLUGIN_NAME = "stub1";

        // Register Stub plugin in the platform
        ReaderPlugin stubPlugin =
                seProxyService.registerPlugin(new StubPluginFactory(STUB_PLUGIN_NAME));

        // Plug the PO stub reader.
        ((StubPlugin) stubPlugin).plugStubReader("poReader", true);

        // Get a PO reader ready to work with Calypso PO.
        StubReader poReader = (StubReader) (stubPlugin.getReader("poReader"));

        // Check if the reader exists
        if (poReader == null) {
            throw new IllegalStateException("Bad PO reader setup");
        }

        poReader.addSeProtocolSetting(SeCommonProtocols.PROTOCOL_ISO14443_4,
                StubProtocolSetting.STUB_PROTOCOL_SETTING
                        .get(SeCommonProtocols.PROTOCOL_ISO14443_4));

        // Create 'virtual' Calypso PO
        StubSecureElement calypsoStubSe = new StubCalypsoClassic();

        logger.info("Insert stub PO.");
        poReader.insertSe(calypsoStubSe);

        logger.info(
                "=============== UseCase Calypso #1: AID based explicit selection ==================");
        logger.info("= PO Reader  NAME = {}", poReader.getName());

        // Check if a PO is present in the reader
        if (poReader.isSePresent()) {

            logger.info(
                    "= #### 1st PO exchange: AID based selection with reading of Environment file.");

            // Prepare a Calypso PO selection
            SeSelection seSelection = new SeSelection();

            // Setting of an AID based selection of a Calypso REV3 PO
            // Select the first application matching the selection AID whatever the SE communication
            // protocol keep the logical channel open after the selection

            // Calypso selection: configures a PoSelectionRequest with all the desired attributes to
            // make the selection and read additional information afterwards
            PoSelectionRequest poSelectionRequest = new PoSelectionRequest(PoSelector.builder()
                    .seProtocol(SeCommonProtocols.PROTOCOL_ISO14443_4)
                    .aidSelector(AidSelector.builder().aidToSelect(CalypsoClassicInfo.AID).build())
                    .invalidatedPo(InvalidatedPo.REJECT).build());

            // Prepare the reading order.
            poSelectionRequest.prepareReadRecordFile(CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                    CalypsoClassicInfo.RECORD_NUMBER_1);

            // Add the selection case to the current selection (we could have added other cases
            // here)
            seSelection.prepareSelection(poSelectionRequest);

            // Actual PO communication: operate through a single request the Calypso PO selection
            // and the file read
            CalypsoPo calypsoPo = (CalypsoPo) seSelection.processExplicitSelection(poReader)
                    .getActiveMatchingSe();
            logger.info("The selection of the PO has succeeded.");

            // Retrieve the data read from the CalyspoPo updated during the transaction process
            ElementaryFile efEnvironmentAndHolder =
                    calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder);
            String environmentAndHolder =
                    ByteArrayUtil.toHex(efEnvironmentAndHolder.getData().getContent());

            // Log the result
            logger.info("EnvironmentAndHolder file data: {}", environmentAndHolder);


            // Go on with the reading of the first record of the EventLog file
            logger.info("= #### 2nd PO exchange: reading transaction of the EventLog file.");

            PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, calypsoPo));

            // Prepare the reading order and keep the associated parser for later use once the
            // transaction has been processed.
            poTransaction.prepareReadRecordFile(CalypsoClassicInfo.SFI_EventLog,
                    CalypsoClassicInfo.RECORD_NUMBER_1);

            // Actual PO communication: send the prepared read order, then close the channel with
            // the PO
            poTransaction.processPoCommands(ChannelControl.CLOSE_AFTER);
            logger.info("The reading of the EventLog has succeeded.");

            // Retrieve the data read from the CalyspoPo updated during the transaction process
            ElementaryFile efEventLog = calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EventLog);
            String eventLog = ByteArrayUtil.toHex(efEventLog.getData().getContent());

            // Log the result
            logger.info("EventLog file data: {}", eventLog);

            logger.info("= #### End of the Calypso PO processing.");
        } else {
            logger.error("The selection of the PO has failed.");
        }

        logger.info("Remove stub PO.");
        poReader.removeSe();

        System.exit(0);
    }
}
