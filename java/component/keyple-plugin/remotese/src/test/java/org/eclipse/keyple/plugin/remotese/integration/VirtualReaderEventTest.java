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
package org.eclipse.keyple.plugin.remotese.integration;

import static org.eclipse.keyple.plugin.stub.StubReaderTest.hoplinkSE;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.core.selection.AbstractMatchingSe;
import org.eclipse.keyple.core.selection.AbstractSeSelectionRequest;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.selection.SelectionsResult;
import org.eclipse.keyple.core.seproxy.ChannelControl;
import org.eclipse.keyple.core.seproxy.MultiSeRequestProcessing;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.SeSelector;
import org.eclipse.keyple.core.seproxy.event.DefaultSelectionsResponse;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.event.ReaderEvent;
import org.eclipse.keyple.core.seproxy.exception.KeypleIOReaderException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.message.SeResponse;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.core.seproxy.protocol.TransmissionMode;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.eclipse.keyple.plugin.remotese.pluginse.VirtualObservableReader;
import org.eclipse.keyple.plugin.stub.StubReader;
import org.eclipse.keyple.plugin.stub.StubReaderTest;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Virtual Reader Service with stub plugin and hoplink SE
 */
public class VirtualReaderEventTest extends VirtualReaderBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(VirtualReaderEventTest.class);

    private VirtualObservableReader virtualReader;
    private StubReader nativeReader;

    /**
     * Create a new class extending AbstractSeSelectionRequest
     */
    private class GenericSeSelectionRequest extends AbstractSeSelectionRequest {
        TransmissionMode transmissionMode;

        public GenericSeSelectionRequest(SeSelector seSelector) {
            super(seSelector);
            transmissionMode = seSelector.getSeProtocol().getTransmissionMode();
        }

        @Override
        protected AbstractMatchingSe parse(SeResponse seResponse) {
            class GenericMatchingSe extends AbstractMatchingSe {
                public GenericMatchingSe(SeResponse selectionResponse,
                        TransmissionMode transmissionMode, String extraInfo) {
                    super(selectionResponse, transmissionMode, extraInfo);
                }
            }
            return new GenericMatchingSe(seResponse, transmissionMode, "Generic Matching SE");
        }
    }

    /*
     * SE EVENTS
     */

    @Before
    public void setUp() throws Exception {
        Assert.assertEquals(0, SeProxyService.getInstance().getPlugins().size());

        initMasterNSlave();

        /*
         * connect stub reader to create virtual reader
         */
        nativeReader = this.connectStubReader(NATIVE_READER_NAME, CLIENT_NODE_ID,
                TransmissionMode.CONTACTLESS);

        // get virtual reader
        virtualReader = (VirtualObservableReader) getVirtualReader();

    }

    @After
    public void tearDown() throws Exception {
        disconnectReader(NATIVE_READER_NAME);

        clearMasterNSlave();

        unregisterPlugins();

        Assert.assertEquals(0, SeProxyService.getInstance().getPlugins().size());
    }


    /**
     * Test SE_INSERTED Reader Event throwing and catching
     *
     * @throws Exception
     */
    @Test
    public void testInsert() throws Exception {

        // lock test until message is received
        final CountDownLatch lock = new CountDownLatch(1);

        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {
                Assert.assertEquals(event.getReaderName(), virtualReader.getName());
                Assert.assertEquals(event.getPluginName(), masterAPI.getPlugin().getName());
                Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
                logger.debug("Reader Event is correct, release lock");
                lock.countDown();
            }
        };

        // register stubPluginObserver
        virtualReader.addObserver(obs);

        logger.info("Insert a Hoplink SE and wait 5 seconds for a SE event to be thrown");

        // insert SE
        nativeReader.insertSe(StubReaderTest.hoplinkSE());

        // wait 5 seconds
        lock.await(5, TimeUnit.SECONDS);

        // remove observer
        virtualReader.removeObserver(obs);

        Assert.assertEquals(0, lock.getCount());
    }


    /**
     * Test SE_REMOVED Reader Event throwing and catching
     *
     * @throws Exception
     */
    @Test
    public void testRemoveEvent() throws Exception {

        // lock test until two messages are received
        final CountDownLatch lock = new CountDownLatch(2);

        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {
                if (event.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
                    // we expect the first event to be SE_INSERTED
                    Assert.assertEquals(2, lock.getCount());
                    lock.countDown();
                } else {
                    // the next event should be SE_REMOVED
                    Assert.assertEquals(1, lock.getCount());
                    Assert.assertEquals(event.getReaderName(), virtualReader.getName());
                    Assert.assertEquals(event.getPluginName(), masterAPI.getPlugin().getName());
                    Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
                    logger.debug("Reader Event is correct, release lock");
                    lock.countDown();

                }
            }
        };
        // register stubPluginObserver
        virtualReader.addObserver(obs);

        logger.info(
                "Insert and remove a Hoplink SE and wait 5 seconds for two SE events to be thrown");

        // insert SE
        nativeReader.insertSe(StubReaderTest.hoplinkSE());
        // wait 0,5 second
        Thread.sleep(500);
        nativeReader.notifySeProcessed();

        // remove SE
        nativeReader.removeSe();

        // wait 5 seconds
        lock.await(5, TimeUnit.SECONDS);

        // remove observer
        virtualReader.removeObserver(obs);

        Assert.assertEquals(0, lock.getCount());

        // https://github.com/calypsonet/keyple-java/issues/420
        // Assert.assertEquals(0, masterAPI.getPlugin().getReaders().size());
    }


    @Test
    public void testInsertMatchingSe() throws InterruptedException {

        // CountDown lock
        final CountDownLatch lock = new CountDownLatch(1);
        final String poAid = "A000000291A000000191";

        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {
                Assert.assertEquals(event.getReaderName(), virtualReader.getName());
                Assert.assertEquals(event.getPluginName(), masterAPI.getPlugin().getName());
                Assert.assertEquals(ReaderEvent.EventType.SE_MATCHED, event.getEventType());
                Assert.assertTrue(((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                        .getSelectionSeResponseSet().get(0).getSelectionStatus().hasMatched());

                Assert.assertArrayEquals(
                        ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                                .getSelectionSeResponseSet().get(0).getSelectionStatus().getAtr()
                                .getBytes(),
                        hoplinkSE().getATR());

                // retrieve the expected FCI from the Stub SE running the select application command
                byte[] aid = ByteArrayUtil.fromHex(poAid);
                byte[] selectApplicationCommand = new byte[6 + aid.length];
                selectApplicationCommand[0] = (byte) 0x00; // CLA
                selectApplicationCommand[1] = (byte) 0xA4; // INS
                selectApplicationCommand[2] = (byte) 0x04; // P1: select by name
                selectApplicationCommand[3] = (byte) 0x00; // P2: requests the first
                selectApplicationCommand[4] = (byte) (aid.length); // Lc
                System.arraycopy(aid, 0, selectApplicationCommand, 5, aid.length); // data

                selectApplicationCommand[5 + aid.length] = (byte) 0x00; // Le
                byte[] fci = null;
                try {
                    fci = hoplinkSE().processApdu(selectApplicationCommand);
                } catch (KeypleIOReaderException e) {
                    e.printStackTrace();
                }

                Assert.assertArrayEquals(
                        ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                                .getSelectionSeResponseSet().get(0).getSelectionStatus().getFci()
                                .getBytes(),
                        fci);

                logger.debug("match event is correct");
                // unlock thread
                lock.countDown();
            }
        };

        // register observer
        virtualReader.addObserver(obs);

        SeSelection seSelection = new SeSelection();

        GenericSeSelectionRequest genericSeSelectionRequest = new GenericSeSelectionRequest(
                new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                        new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null),
                        "AID: " + poAid));

        seSelection.prepareSelection(genericSeSelectionRequest);

        ((ObservableReader) virtualReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(),
                ObservableReader.NotificationMode.MATCHED_ONLY);

        // wait 1 second
        Thread.sleep(1000);

        // test
        nativeReader.insertSe(StubReaderTest.hoplinkSE());

        // lock thread for 2 seconds max to wait for the event
        lock.await(5, TimeUnit.SECONDS);

        // remove observer
        virtualReader.removeObserver(obs);

        Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
        // observer

    }


    @Test
    public void testInsertNotMatching_MatchedOnly() throws InterruptedException {

        // CountDown lock
        final CountDownLatch lock = new CountDownLatch(1);

        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {
                // no event should be thrown
                Assert.fail();
                lock.countDown();// should not be called
            }
        };

        // register observer
        virtualReader.addObserver(obs);

        String poAid = "A000000291A000000192";// not matching poAid

        SeSelection seSelection = new SeSelection();

        GenericSeSelectionRequest genericSeSelectionRequest = new GenericSeSelectionRequest(
                new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                        new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null),
                        "AID: " + poAid));

        seSelection.prepareSelection(genericSeSelectionRequest);

        ((ObservableReader) virtualReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(),
                ObservableReader.NotificationMode.MATCHED_ONLY);

        // wait 1 second
        logger.debug("Wait 1 second before inserting SE");
        Thread.sleep(500);

        // test
        nativeReader.insertSe(StubReaderTest.hoplinkSE());


        // lock thread for 2 seconds max to wait for the event
        lock.await(3, TimeUnit.SECONDS);

        // remove observer
        virtualReader.removeObserver(obs);

        Assert.assertEquals(1, lock.getCount()); // should be 1 because countDown is never called
    }

    @Test
    public void testInsertNotMatching_Always() throws InterruptedException {

        // CountDown lock
        final CountDownLatch lock = new CountDownLatch(1);

        // register observer
        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {
                Assert.assertEquals(event.getReaderName(), virtualReader.getName());
                Assert.assertEquals(event.getPluginName(), masterAPI.getPlugin().getName());

                // an SE_INSERTED event is thrown
                Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());

                // card has not match
                Assert.assertFalse(
                        ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                                .getSelectionSeResponseSet().get(0).getSelectionStatus()
                                .hasMatched());

                lock.countDown();// should be called
            }
        };

        virtualReader.addObserver(obs);

        String poAid = "A000000291A000000192";// not matching poAid

        SeSelection seSelection = new SeSelection();

        GenericSeSelectionRequest genericSeSelectionRequest = new GenericSeSelectionRequest(
                new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                        new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null),
                        "AID: " + poAid));

        seSelection.prepareSelection(genericSeSelectionRequest);

        ((ObservableReader) virtualReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(), ObservableReader.NotificationMode.ALWAYS);

        // wait 1 second
        logger.debug("Wait 1 second before inserting SE");
        Thread.sleep(500);

        // test
        nativeReader.insertSe(StubReaderTest.hoplinkSE());

        // lock thread for 2 seconds max to wait for the event
        lock.await(5, TimeUnit.SECONDS);

        // remove observer
        virtualReader.removeObserver(obs);

        Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
        // observer

    }

    @Test
    public void testATR() throws InterruptedException {

        // CountDown lock
        final CountDownLatch lock = new CountDownLatch(1);

        ObservableReader.ReaderObserver obs = new ObservableReader.ReaderObserver() {
            @Override
            public void update(ReaderEvent event) {

                Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());

                SeSelection seSelection = new SeSelection(MultiSeRequestProcessing.FIRST_MATCH,
                        ChannelControl.KEEP_OPEN);
                GenericSeSelectionRequest genericSeSelectionRequest = new GenericSeSelectionRequest(
                        new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4,
                                new SeSelector.AtrFilter("3B.*"), null, "Test " + "ATR"));

                /* Prepare selector, ignore AbstractMatchingSe here */
                seSelection.prepareSelection(genericSeSelectionRequest);

                try {
                    SelectionsResult selectionsResult =
                            seSelection.processExplicitSelection(virtualReader);

                    AbstractMatchingSe matchingSe =
                            selectionsResult.getActiveSelection().getMatchingSe();

                    Assert.assertNotNull(matchingSe);

                } catch (KeypleReaderException e) {
                    Assert.fail("Unexcepted exception");
                }
                // unlock thread
                lock.countDown();
            }
        };

        // register observer
        virtualReader.addObserver(obs);

        // wait 1 second
        logger.debug("Wait 1 second before inserting SE");
        Thread.sleep(500);

        // test
        nativeReader.insertSe(StubReaderTest.hoplinkSE());

        // lock thread for 2 seconds max to wait for the event
        lock.await(5, TimeUnit.SECONDS);
        Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by

        // remove observer
        virtualReader.removeObserver(obs);

    }


}
