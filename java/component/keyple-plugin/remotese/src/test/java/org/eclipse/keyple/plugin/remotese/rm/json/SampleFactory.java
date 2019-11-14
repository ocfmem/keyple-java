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
package org.eclipse.keyple.plugin.remotese.rm.json;


import java.io.IOException;
import java.util.*;
import org.eclipse.keyple.core.seproxy.SeSelector;
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsRequest;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.message.*;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.core.util.ByteArrayUtil;

public class SampleFactory {

    public static KeypleBaseException getAStackedKeypleException() {
        return new KeypleReaderException("Keyple Reader Exception", new IOException("IO Error",
                new IOException("IO Error2", new RuntimeException("sdfsdf"))));
    }

    public static KeypleBaseException getASimpleKeypleException() {
        return new KeypleReaderException("Keyple Reader Exception");
    }

    public static AbstractDefaultSelectionsRequest getSelectionRequest() {
        return new DefaultSelectionsRequest(getASeRequestSet_ISO14443_4());
    }

    public static ObservableReader.NotificationMode getNotificationMode() {
        return ObservableReader.NotificationMode.ALWAYS;
    }

    public static Set<SeRequest> getASeRequestSet_ISO14443_4() {
        String poAid = "A000000291A000000191";

        List<ApduRequest> poApduRequestList;
        poApduRequestList = Arrays.asList(new ApduRequest(ByteArrayUtil.fromHex("9000"), true));

        SeSelector seSelector = new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null), null);

        SeRequest seRequest = new SeRequest(seSelector, poApduRequestList);

        Set<SeRequest> seRequestSet = new LinkedHashSet<SeRequest>();

        seRequestSet.add(seRequest);

        return seRequestSet;

    }


    public static Set<SeRequest> getASeRequestSet() {
        String poAid = "A000000291A000000191";

        List<ApduRequest> poApduRequestList;
        poApduRequestList = Arrays.asList(new ApduRequest(ByteArrayUtil.fromHex("9000"), true));

        SeRequest seRequest = new SeRequest(poApduRequestList);

        Set<SeRequest> seRequestSet = new LinkedHashSet<SeRequest>();

        seRequestSet.add(seRequest);

        return seRequestSet;

    }

    public static SeRequest getASeRequest_ISO14443_4() {
        String poAid = "A000000291A000000191";

        List<ApduRequest> poApduRequestList;
        poApduRequestList = Arrays.asList(new ApduRequest(ByteArrayUtil.fromHex("9000"), true));

        SeSelector seSelector = new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null), null);

        SeRequest seRequest = new SeRequest(seSelector, poApduRequestList);
        return seRequest;

    }

    public static SeRequest getASeRequest() {
        String poAid = "A000000291A000000191";

        List<ApduRequest> poApduRequestList;
        poApduRequestList = Arrays.asList(new ApduRequest(ByteArrayUtil.fromHex("9000"), true));

        SeRequest seRequest = new SeRequest(poApduRequestList);
        return seRequest;

    }

    public static Set<SeRequest> getCompleteRequestSet() {
        String poAid = "A000000291A000000191";

        List<ApduRequest> poApduRequestList;

        poApduRequestList = Arrays.asList(new ApduRequest(ByteArrayUtil.fromHex("9000"), true));

        SeSelector aidSelector = new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(poAid), null), null);

        SeSelector atrSelector = new SeSelector(SeCommonProtocols.PROTOCOL_ISO7816_3,
                new SeSelector.AtrFilter("/regex/"), null, null);

        SeRequest seRequest = new SeRequest(aidSelector, poApduRequestList);

        SeRequest seRequest2 = new SeRequest(atrSelector, poApduRequestList);

        Set<SeRequest> seRequests = new HashSet<SeRequest>();
        seRequests.add(seRequest);
        seRequests.add(seRequest2);

        Set<SeRequest> seRequestSet = new LinkedHashSet<SeRequest>();

        seRequestSet.add(seRequest);

        return seRequestSet;


    }

    public static List<SeResponse> getCompleteResponseSet() {
        List<SeResponse> seResponses = new ArrayList<SeResponse>();

        ApduResponse apdu = new ApduResponse(ByteArrayUtil.fromHex("9000"), new HashSet<Integer>());
        ApduResponse apdu2 =
                new ApduResponse(ByteArrayUtil.fromHex("9000"), new HashSet<Integer>());

        List<ApduResponse> apduResponses = new ArrayList<ApduResponse>();
        apduResponses.add(apdu);
        apduResponses.add(apdu2);

        seResponses.add(
                new SeResponse(true, true, new SelectionStatus(null, apdu, true), apduResponses));
        seResponses.add(
                new SeResponse(true, true, new SelectionStatus(null, apdu, true), apduResponses));

        return seResponses;


    }



}
