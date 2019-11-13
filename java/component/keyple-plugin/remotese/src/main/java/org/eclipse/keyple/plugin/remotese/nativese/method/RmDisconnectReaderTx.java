/********************************************************************************
 * Copyright (c) 2019 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.plugin.remotese.nativese.method;

import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.plugin.remotese.exception.KeypleRemoteException;
import org.eclipse.keyple.plugin.remotese.rm.AbstractRemoteMethodTx;
import org.eclipse.keyple.plugin.remotese.rm.RemoteMethodName;
import org.eclipse.keyple.plugin.remotese.transport.json.JsonParser;
import org.eclipse.keyple.plugin.remotese.transport.model.KeypleDto;
import org.eclipse.keyple.plugin.remotese.transport.model.KeypleDtoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;

/**
 * Handle the Disconnect Reader keypleDTO serialization and deserialization
 */
public class RmDisconnectReaderTx extends AbstractRemoteMethodTx<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(RmDisconnectReaderTx.class);

    @Override
    public RemoteMethodName getMethodName() {
        return RemoteMethodName.READER_DISCONNECT;
    }


    public RmDisconnectReaderTx(String sessionId, String nativeReaderName, String slaveNodeId,
            String targetNodeId) {
        super(sessionId, nativeReaderName, null, targetNodeId, slaveNodeId);
    }

    @Override
    public Boolean parseResponse(KeypleDto keypleDto) throws KeypleRemoteException {
        // if reader connection thrown an exception
        if (KeypleDtoHelper.containsException(keypleDto)) {
            // logger.trace("KeypleDto contains an exception: {}", keypleDto);
            KeypleReaderException ex =
                    JsonParser.getGson().fromJson(keypleDto.getBody(), KeypleReaderException.class);
            throw new KeypleRemoteException(
                    "An exception occurs while calling the remote method disconnectReader", ex);
        } else {
            JsonObject body = JsonParser.getGson().fromJson(keypleDto.getBody(), JsonObject.class);
            return body.has("status") && body.get("status").getAsBoolean();
        }

    }

    @Override
    public KeypleDto dto() {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);

        return KeypleDtoHelper.buildRequest(getMethodName().getName(),
                JsonParser.getGson().toJson(body, JsonObject.class), null, nativeReaderName, null,
                requesterNodeId, targetNodeId, id);
    }
}
