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
package org.eclipse.keyple.example.generic.pc;


import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import org.eclipse.keyple.core.seproxy.ReaderPlugin;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.core.seproxy.protocol.SeProtocolSetting;
import org.eclipse.keyple.example.generic.common.CustomPluginSetting;
import org.eclipse.keyple.example.generic.common.SeProtocolDetectionEngine;
import org.eclipse.keyple.example.generic.pc.stub.se.*;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubProtocolSetting;
import org.eclipse.keyple.plugin.stub.StubReader;

/**
 * This class handles the reader events generated by the SeProxyService
 */
public class

Demo_SeProtocolDetection_Stub {

    private SeReader poReader, samReader;

    public Demo_SeProtocolDetection_Stub() {
        super();
    }

    /**
     * Application entry
     *
     * @param args the program arguments
     * @throws IllegalArgumentException in case of a bad argument
     * @throws InterruptedException if thread error occurs
     */
    public static void main(String[] args) throws IllegalArgumentException, InterruptedException {
        /* get the SeProxyService instance */
        SeProxyService seProxyService = SeProxyService.getInstance();

        /* add the PcscPlugin to the SeProxyService */
        SortedSet<ReaderPlugin> pluginsSet = new ConcurrentSkipListSet<ReaderPlugin>();

        StubPlugin stubPlugin = StubPlugin.getInstance();

        pluginsSet.add(stubPlugin);

        seProxyService.setPlugins(pluginsSet);

        /* create an observer class to handle the SE operations */
        SeProtocolDetectionEngine observer = new SeProtocolDetectionEngine();

        /*
         * Plug PO reader.
         */
        stubPlugin.plugStubReader("poReader", true);

        Thread.sleep(200);

        StubReader poReader = null, samReader = null;
        try {
            poReader = (StubReader) (stubPlugin.getReader("poReader"));
        } catch (KeypleReaderNotFoundException e) {
            e.printStackTrace();
        }

        observer.setReader(poReader);

        // Protocol detection settings.
        // add 8 expected protocols with three different methods:
        // - adding protocols individually
        // - using a custom enum
        // A real application should use only one method.

        // Method 1
        // add protocols individually
        poReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_MEMORY_ST25));

        poReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_ISO14443_4));

        poReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_MIFARE_CLASSIC));

        poReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_MIFARE_UL));


        // Method 2
        // add all settings at once with setting enum
        poReader.addSeProtocolSetting(new SeProtocolSetting(CustomPluginSetting.values()));

        // Set terminal as Observer of the first reader
        ((ObservableReader) poReader).addObserver(observer);

        // poReader.insertSe(new StubCalypsoClassic());
        //
        // Thread.sleep(300);
        //
        // poReader.removeSe();

        // Thread.sleep(100);
        //
        // poReader.insertSe(new StubCalypsoBPrime());

        Thread.sleep(300);

        poReader.removeSe();

        Thread.sleep(100);

        poReader.insertSe(new StubMifareClassic());

        Thread.sleep(300);

        poReader.removeSe();

        Thread.sleep(100);

        /* insert Mifare UltraLight */
        poReader.insertSe(new StubMifareUL());

        Thread.sleep(300);

        poReader.removeSe();

        Thread.sleep(100);

        /* insert Mifare Desfire */
        poReader.insertSe(new StubMifareDesfire());

        Thread.sleep(300);

        poReader.removeSe();

        Thread.sleep(100);



        System.exit(0);
    }
}
