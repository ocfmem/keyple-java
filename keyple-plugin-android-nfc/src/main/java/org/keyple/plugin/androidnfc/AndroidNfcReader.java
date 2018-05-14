/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.keyple.plugin.androidnfc;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.keyple.seproxy.ApduRequest;
import org.keyple.seproxy.ApduResponse;
import org.keyple.seproxy.ByteBufferUtils;
import org.keyple.seproxy.ObservableReader;
import org.keyple.seproxy.ReaderEvent;
import org.keyple.seproxy.SeRequest;
import org.keyple.seproxy.SeRequestElement;
import org.keyple.seproxy.SeResponse;
import org.keyple.seproxy.SeResponseElement;
import org.keyple.seproxy.exceptions.IOReaderException;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.util.Log;


/**
 * Implementation of @{@link org.keyple.seproxy.ProxyReader} for the communication with the ISO Card
 * though Android @{@link NfcAdapter}
 */
public class AndroidNfcReader extends ObservableReader implements NfcAdapter.ReaderCallback {

    private static final String TAG = "AndroidNfcReader";

    // keep state between session if required
    private TagTransceiver tagTransceiver;
    private ByteBuffer previousOpenApplication = null;


    /**
     * Private constructor
     */
    private AndroidNfcReader() {
        Log.i(TAG, "Instanciate singleton NFC Reader");
    }

    /**
     * Holder of singleton
     */
    private static class SingletonHolder {
        /**
         * Unique instance no-preinitialized
         */
        private final static AndroidNfcReader instance = new AndroidNfcReader();
    }


    /**
     * Access point for the unique instance of singleton
     */
    protected static AndroidNfcReader getInstance() {
        return SingletonHolder.instance;
    }


    @Override
    public String getName() {

        return "AndroidNfcReader";
    }


    /**
     * Callback function invoked when the @{@link NfcAdapter} detects a @{@link Tag}
     * 
     * @param tag : detected tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {

        Log.i(TAG, "Received Tag Discovered event " + printTagId());
        connectTag(tag);
    }


    @Override
    public boolean isSEPresent() throws IOReaderException {
        return tagTransceiver != null && tagTransceiver.isConnected();
    }

    /**
     * Transmit {@link SeRequest} to the connected Tag Supports protocol argument to
     * filterByProtocol commands for the right connected Tag
     * 
     * @param seRequest the se application request
     * @return {@link SeResponse} : response from the transmitted request
     */
    @Override
    public SeResponse transmit(SeRequest seRequest) {
        Log.i(TAG, "Calling transmit on Android NFC Reader");
        Log.d(TAG, "Size of APDU Requests : " + String.valueOf(seRequest.getElements().size()));

        // init response
        List<SeResponseElement> seResponseElements = new ArrayList<SeResponseElement>();

        // Filter requestElements whom protocol matches the current tag
        List<SeRequestElement> seRequestElements = filterByProtocol(seRequest.getElements());

        // no seRequestElements are left after filtering
        if (seRequestElements.size() < 1) {
            disconnectTag();
            return new SeResponse(seResponseElements);

        }


        // process the request elements
        for (int i = 0; i < seRequestElements.size(); i++) {

            SeRequestElement seRequestElement = seRequestElements.get(i);

            // init response
            List<ApduResponse> apduResponses = new ArrayList<ApduResponse>();
            ApduResponse fciResponse = null;

            try {

                // Checking of the presence of the AID request in requests group
                ByteBuffer aid = seRequestElement.getAidToSelect();

                // Open the application channel if not open yet
                if (previousOpenApplication == null || previousOpenApplication != aid) {
                    Log.i(TAG, "Connecting to application : " + aid);
                    fciResponse = this.connectApplication(seRequestElement.getAidToSelect());
                }

                // Send all apduRequest
                for (ApduRequest apduRequest : seRequestElement.getApduRequests()) {
                    apduResponses.add(sendAPDUCommand(apduRequest.getBuffer()));
                }

                // Add ResponseElements to global SeResponse
                SeResponseElement out = new SeResponseElement(previousOpenApplication != null,
                        fciResponse, apduResponses);
                seResponseElements.add(out);

                // Don't process more seRequestElement if asked
                if (seRequestElement.keepChannelOpen()) {
                    Log.i(TAG,
                            "Keep Channel Open is set to true, abort further seRequestElement if any");
                    saveChannelState(aid);
                    break;
                }

                // For last element, close physical channel if asked
                if (i == seRequestElements.size() - 1 && !seRequestElement.keepChannelOpen()) {
                    disconnectTag();
                }

            } catch (IOException e) {
                Log.e(TAG, "Error executing command");
                e.printStackTrace();
                apduResponses.add(null);// add empty response
            }

        }

        return new SeResponse(seResponseElements);
    }


    /**
     * Filter seRequestElements based on their protocol and the tag detected
     * 
     * @param seRequestElements embedding seRequestElements to be filtered
     * @return filtered seRequest
     */
    private List<SeRequestElement> filterByProtocol(List<SeRequestElement> seRequestElements) {


        Log.d(TAG, "Filtering # seRequestElements : " + seRequestElements.size());
        List<SeRequestElement> filteredSRE = new ArrayList<SeRequestElement>();

        for (SeRequestElement seRequestElement : seRequestElements) {

            Log.d(TAG, "Filtering seRequestElement whom protocol : "
                    + seRequestElement.getProtocolFlag());

            if (seRequestElement.getProtocolFlag() != null
                    && seRequestElement.getProtocolFlag().equals(tagTransceiver.getTech())) {
                filteredSRE.add(seRequestElement);
            }
        }
        Log.d(TAG, "After Filter seRequestElement : " + filteredSRE.size());
        return filteredSRE;

    }


    /**
     * method to connect to the card from the terminal
     *
     * @param aid the AID application
     */
    private ApduResponse connectApplication(ByteBuffer aid) throws IOException {

        Log.i(TAG, "Connecting to application : " + ByteBufferUtils.toHex(aid));

        ByteBuffer command = ByteBuffer.allocate(aid.limit() + 6);
        command.put((byte) 0x00);
        command.put((byte) 0xA4);
        command.put((byte) 0x04);
        command.put((byte) 0x00);
        command.put((byte) aid.limit());
        command.put(aid);
        command.put((byte) 0x00);
        command.position(0);

        return sendAPDUCommand(command);

    }



    /**
     * Process data from NFC Intent
     *
     * @param intent : Intent received and filterByProtocol by xml tech_list
     */
    protected void processIntent(Intent intent) {

        // Extract Tag from Intent
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        this.connectTag(tag);
    }


    /**
     * Connect to the tag (physical connect)
     */
    private void connectTag(Tag tag) {


        try {

            tagTransceiver = TagTransceiver.getTagTransceiver(tag);
            tagTransceiver.connect();

            Log.i(TAG, "Tag connected successfully : " + printTagId());

            notifyObservers(new ReaderEvent(AndroidNfcReader.getInstance(),
                    ReaderEvent.EventType.SE_INSERTED));

        } catch (IOException e) {
            Log.e(TAG, "Error while connecting to Tag ");
            e.printStackTrace();
        }
    }



    /**
     * Disconnect from the tag (physical disconnect)
     */
    private void disconnectTag() {
        try {

            if (tagTransceiver != null) {

                tagTransceiver.close();
                this.notifyObservers(new ReaderEvent(this, ReaderEvent.EventType.SE_REMOVAL));

                Log.i(TAG, "Disconnected tag : " + printTagId());
            }

        } catch (IOException e) {
            Log.e(TAG, "Disconnecting error");
        }

        tagTransceiver = null;
    }

    /**
     * Keep the current channel open for further commands
     */
    private void saveChannelState(ByteBuffer aid) {
        Log.d(TAG, "save application id for further commands");
        previousOpenApplication = aid;

    }

    /**
     * Exchanges of APDU cmds with the ISO tag/card
     *
     * @param command command to send
     * 
     */
    private ApduResponse sendAPDUCommand(ByteBuffer command) throws IOException {
        // Initialization
        long commandLenght = command.limit();
        Log.d(TAG, "Data Length to be sent to tag : " + commandLenght);
        byte[] data = ByteBufferUtils.toBytes(command);
        Log.i(TAG, "Data in : " + data);
        byte[] dataOut = tagTransceiver.transceive(data);
        Log.i(TAG, "Data out  : " + dataOut);
        return new ApduResponse(dataOut, true);

    }

    private String printTagId() {
        return tagTransceiver != null
                ? tagTransceiver.getTag().getId() + tagTransceiver.getTag().toString()
                : "null";
    }
}
