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
package org.eclipse.keyple.calypso.transaction;


import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.eclipse.keyple.calypso.SelectFileControl;
import org.eclipse.keyple.calypso.command.PoClass;
import org.eclipse.keyple.calypso.command.po.AbstractPoCommandBuilder;
import org.eclipse.keyple.calypso.command.po.AbstractPoResponseParser;
import org.eclipse.keyple.calypso.command.po.builder.*;
import org.eclipse.keyple.calypso.command.po.builder.security.AbstractOpenSessionCmdBuild;
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException;
import org.eclipse.keyple.calypso.command.po.parser.AppendRecordRespPars;
import org.eclipse.keyple.calypso.command.po.parser.DecreaseRespPars;
import org.eclipse.keyple.calypso.command.po.parser.IncreaseRespPars;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.command.po.parser.SelectFileRespPars;
import org.eclipse.keyple.calypso.command.po.parser.UpdateRecordRespPars;
import org.eclipse.keyple.calypso.command.po.parser.WriteRecordRespPars;
import org.eclipse.keyple.calypso.command.po.parser.security.AbstractOpenSessionRespPars;
import org.eclipse.keyple.core.seproxy.message.ApduResponse;
import org.eclipse.keyple.core.util.Assert;

/**
 * Utility class used to check Calypso specific data.
 */
final class CalypsoPoUtils {
    public static final int MASK_3_BITS = 0x7; // 7
    public static final int MASK_4_BITS = 0xF; // 15
    public static final int MASK_5_BITS = 0x1F; // 31
    public static final int MASK_7_BITS = 0x7F; // 127
    public static final int MASK_1_BYTE = 0xFF; // 255
    public static final int MASK_2_BYTES = 0xFFFF;
    public static final int MASK_3_BYTES = 0xFFFFFF;

    // SFI
    public static final int SFI_MIN = 0;
    public static final int SFI_MAX = MASK_5_BITS;
    // Record number
    public static final int NB_REC_MIN = 1;
    public static final int NB_REC_MAX = 255;

    // Counter number
    public static final int NB_CNT_MIN = 1;
    public static final int NB_CNT_MAX = 255;

    // Counter value
    public static final int CNT_VALUE_MIN = 0;
    public static final int CNT_VALUE_MAX = 16777215;

    // Le max
    public static final int LE_MAX = 255;

    // File Type Values
    public static final int FILE_TYPE_MF = 1;
    public static final int FILE_TYPE_DF = 2;
    public static final int FILE_TYPE_EF = 4;

    // EF Type Values
    public static final int EF_TYPE_DF = 0;
    public static final int EF_TYPE_BINARY = 1;
    public static final int EF_TYPE_LINEAR = 2;
    public static final int EF_TYPE_CYCLIC = 4;
    public static final int EF_TYPE_SIMULATED_COUNTERS = 8;
    public static final int EF_TYPE_COUNTERS = 9;

    // Field offsets in select file response (tag/length excluded)
    public static final int SEL_SFI_OFFSET = 0;
    public static final int SEL_TYPE_OFFSET = 1;
    public static final int SEL_EF_TYPE_OFFSET = 2;
    public static final int SEL_REC_SIZE_OFFSET = 3;
    public static final int SEL_NUM_REC_OFFSET = 4;
    public static final int SEL_AC_OFFSET = 5;
    public static final int SEL_AC_LENGTH = 4;
    public static final int SEL_NKEY_OFFSET = 9;
    public static final int SEL_NKEY_LENGTH = 4;
    public static final int SEL_DF_STATUS_OFFSET = 13;
    public static final int SEL_KVCS_OFFSET = 14;
    public static final int SEL_KIFS_OFFSET = 17;
    public static final int SEL_DATA_REF_OFFSET = 14;
    public static final int SEL_LID_OFFSET = 21;


    /**
     * Private constructor
     */
    private CalypsoPoUtils() {

    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Open Secure Session command
     * received from the PO <br>
     * The ratification status and the data read at the time of the session opening are added to the
     * CalypsoPo.
     *
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param openSessionCmdBuild the Open Secure Session command builder
     * @param apduResponse the response received
     * @return the created response parser
     */
    private static AbstractOpenSessionRespPars updateCalypsoPoOpenSession(CalypsoPo calypsoPo,
            AbstractOpenSessionCmdBuild<AbstractOpenSessionRespPars> openSessionCmdBuild,
            ApduResponse apduResponse) {
        // create parser
        AbstractOpenSessionRespPars openSessionRespPars =
                openSessionCmdBuild.createResponseParser(apduResponse);

        calypsoPo.setDfRatified(openSessionRespPars.wasRatified());

        byte[] recordDataRead = openSessionRespPars.getRecordDataRead();

        if (recordDataRead.length > 0) {
            calypsoPo.setContent((byte) openSessionCmdBuild.getSfi(),
                    openSessionCmdBuild.getRecordNumber(), recordDataRead);
        }

        return openSessionRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Read Records command received
     * from the PO <br>
     * The records read are added to the {@link CalypsoPo} file structure
     *
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param readRecordsCmdBuild the Read Records command builder
     * @param apduResponse the response received
     * @return the created response parser
     */
    private static ReadRecordsRespPars updateCalypsoPoReadRecords(CalypsoPo calypsoPo,
            ReadRecordsCmdBuild readRecordsCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        // create parser
        ReadRecordsRespPars readRecordsRespPars =
                readRecordsCmdBuild.createResponseParser(apduResponse);

        readRecordsRespPars.checkStatus();

        // iterate over read records to fill the CalypsoPo
        for (Map.Entry<Integer, byte[]> entry : readRecordsRespPars.getRecords().entrySet()) {
            calypsoPo.setContent((byte) readRecordsCmdBuild.getSfi(), entry.getKey(),
                    entry.getValue());
        }
        return readRecordsRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Select File command received from
     * the PO <br>
     * Depending on the content of the response, either a {@link FileHeader} is added or the
     * {@link DirectoryHeader} is updated
     *
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param selectFileCmdBuild the Select File command builder
     * @param apduResponse the response received
     */
    private static SelectFileRespPars updateCalypsoPoSelectFile(CalypsoPo calypsoPo,
            SelectFileCmdBuild selectFileCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        SelectFileRespPars selectFileRespPars =
                selectFileCmdBuild.createResponseParser(apduResponse);

        selectFileRespPars.checkStatus();

        byte[] proprietaryInformation = selectFileRespPars.getProprietaryInformation();
        byte sfi = proprietaryInformation[SEL_SFI_OFFSET];
        byte fileType = proprietaryInformation[SEL_TYPE_OFFSET];
        switch (fileType) {
            case FILE_TYPE_MF:
            case FILE_TYPE_DF:
                DirectoryHeader directoryHeader = createDirectoryHeader(proprietaryInformation);
                calypsoPo.setDirectoryHeader(directoryHeader);
                break;
            case FILE_TYPE_EF:
                FileHeader fileHeader = createFileHeader(proprietaryInformation);
                calypsoPo.setFileHeader(sfi, fileHeader);
                break;
            default:
                throw new IllegalStateException(
                        String.format("Unknown file type: 0x%02X", fileType));
        }
        return selectFileRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Update Record command sent and
     * received from the PO <br>
     * The records read are added to the {@link CalypsoPo} file structure
     *
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param updateRecordCmdBuild the Update Record command builder
     * @param apduResponse the response received
     */
    private static UpdateRecordRespPars updateCalypsoPoUpdateRecord(CalypsoPo calypsoPo,
            UpdateRecordCmdBuild updateRecordCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        UpdateRecordRespPars updateRecordRespPars =
                updateRecordCmdBuild.createResponseParser(apduResponse);

        updateRecordRespPars.checkStatus();

        calypsoPo.setContent((byte) updateRecordCmdBuild.getSfi(),
                updateRecordCmdBuild.getRecordNumber(), updateRecordCmdBuild.getData());

        return updateRecordRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Write Record command sent and
     * received from the PO <br>
     * The records read are added to the {@link CalypsoPo} file structure
     *
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param writeRecordCmdBuild the Write Record command builder
     * @param apduResponse the response received
     */
    private static WriteRecordRespPars updateCalypsoPoWriteRecord(CalypsoPo calypsoPo,
            WriteRecordCmdBuild writeRecordCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        WriteRecordRespPars writeRecordRespPars =
                writeRecordCmdBuild.createResponseParser(apduResponse);

        writeRecordRespPars.checkStatus();

        // TODO we should add another method to Calypso to emulate the behavior of Write Record
        calypsoPo.setContent((byte) writeRecordCmdBuild.getSfi(),
                writeRecordCmdBuild.getRecordNumber(), writeRecordCmdBuild.getData());

        return writeRecordRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Read Records command received
     * from the PO <br>
     * The records read are added to the {@link CalypsoPo} file structure
     *
     * @param appendRecordCmdBuild the Append Records command builder
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param apduResponse the response received
     */
    private static AppendRecordRespPars updateCalypsoPoAppendRecord(CalypsoPo calypsoPo,
            AppendRecordCmdBuild appendRecordCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        AppendRecordRespPars appendRecordRespPars =
                appendRecordCmdBuild.createResponseParser(apduResponse);

        appendRecordRespPars.checkStatus();

        calypsoPo.addCyclicContent((byte) appendRecordCmdBuild.getSfi(),
                appendRecordCmdBuild.getData());

        return appendRecordRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to a Decrease command received from
     * the PO <br>
     * The counter value is updated in the {@link CalypsoPo} file structure
     *
     * @param decreaseCmdBuild the Decrease command builder
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param apduResponse the response received
     */
    private static DecreaseRespPars updateCalypsoPoDecrease(CalypsoPo calypsoPo,
            DecreaseCmdBuild decreaseCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        DecreaseRespPars decreaseRespPars = decreaseCmdBuild.createResponseParser(apduResponse);

        decreaseRespPars.checkStatus();

        calypsoPo.setContent((byte) decreaseCmdBuild.getSfi(), 1, apduResponse.getDataOut(),
                3 * (decreaseCmdBuild.getCounterNumber() - 1));

        return decreaseRespPars;
    }

    /**
     * Updated the {@link CalypsoPo} object with the response to an Increase command received from
     * the PO <br>
     * The counter value is updated in the {@link CalypsoPo} file structure
     *
     * @param increaseCmdBuild the Increase command builder
     * @param calypsoPo the {@link CalypsoPo} object to update
     * @param apduResponse the response received
     */
    private static IncreaseRespPars updateCalypsoPoIncrease(CalypsoPo calypsoPo,
            IncreaseCmdBuild increaseCmdBuild, ApduResponse apduResponse)
            throws CalypsoPoCommandException {
        IncreaseRespPars increaseRespPars = increaseCmdBuild.createResponseParser(apduResponse);

        increaseRespPars.checkStatus();

        calypsoPo.setContent((byte) increaseCmdBuild.getSfi(), 1, apduResponse.getDataOut(),
                3 * (increaseCmdBuild.getCounterNumber() - 1));

        return increaseRespPars;
    }


    /**
     * Parses the proprietaryInformation field of a file identified as an DF and create a
     * {@link DirectoryHeader}
     *
     * @param proprietaryInformation from the response to a Select File command
     * @return a {@link DirectoryHeader} object
     */
    private static DirectoryHeader createDirectoryHeader(byte[] proprietaryInformation) {
        byte[] accessConditions = new byte[SEL_AC_LENGTH];
        System.arraycopy(proprietaryInformation, SEL_AC_OFFSET, accessConditions, 0, SEL_AC_LENGTH);

        byte[] keyIndexes = new byte[SEL_NKEY_LENGTH];
        System.arraycopy(proprietaryInformation, SEL_NKEY_OFFSET, keyIndexes, 0, SEL_NKEY_LENGTH);

        byte dfStatus = proprietaryInformation[SEL_DF_STATUS_OFFSET];

        short lid = (short) (((proprietaryInformation[SEL_LID_OFFSET] << 8) & 0xff00)
                | (proprietaryInformation[SEL_LID_OFFSET + 1] & 0x00ff));

        return DirectoryHeader.builder()//
                .lid(lid)//
                .accessConditions(accessConditions)//
                .keyIndexes(keyIndexes)//
                .dfStatus(dfStatus)//
                .kvc(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_PERSO,
                        proprietaryInformation[SEL_KVCS_OFFSET])//
                .kvc(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD,
                        proprietaryInformation[SEL_KVCS_OFFSET + 1])//
                .kvc(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_DEBIT,
                        proprietaryInformation[SEL_KVCS_OFFSET + 2])//
                .kif(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_PERSO,
                        proprietaryInformation[SEL_KIFS_OFFSET])//
                .kif(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD,
                        proprietaryInformation[SEL_KIFS_OFFSET + 1])//
                .kif(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_DEBIT,
                        proprietaryInformation[SEL_KIFS_OFFSET + 2])//
                .build();
    }

    /**
     * Converts the EF type value from the PO into a {@link FileHeader.FileType} enum
     *
     * @param efType the value returned by the PO
     * @return the corresponding {@link FileHeader.FileType}
     */
    private static FileHeader.FileType getEfTypeFromPoValue(byte efType) {
        FileHeader.FileType fileType;
        switch (efType) {
            case EF_TYPE_BINARY:
                fileType = FileHeader.FileType.BINARY;
                break;
            case EF_TYPE_LINEAR:
                fileType = FileHeader.FileType.LINEAR;
                break;
            case EF_TYPE_CYCLIC:
                fileType = FileHeader.FileType.CYCLIC;
                break;
            case EF_TYPE_SIMULATED_COUNTERS:
                fileType = FileHeader.FileType.SIMULATED_COUNTERS;
                break;
            case EF_TYPE_COUNTERS:
                fileType = FileHeader.FileType.COUNTERS;
                break;
            default:
                throw new IllegalStateException("Unknown EF Type: " + efType);
        }
        return fileType;
    }

    /**
     * Parses the proprietaryInformation field of a file identified as an EF and create a
     * {@link FileHeader}
     *
     * @param proprietaryInformation from the response to a Select File command
     * @return a {@link FileHeader} object
     */
    private static FileHeader createFileHeader(byte[] proprietaryInformation) {

        FileHeader.FileType fileType =
                getEfTypeFromPoValue(proprietaryInformation[SEL_EF_TYPE_OFFSET]);

        int recordSize;
        int recordsNumber;
        if (fileType == FileHeader.FileType.BINARY) {
            recordSize = ((proprietaryInformation[SEL_REC_SIZE_OFFSET] << 8) & 0x0000ff00)
                    | (proprietaryInformation[SEL_NUM_REC_OFFSET] & 0x000000ff);
            recordsNumber = 1;
        } else {
            recordSize = proprietaryInformation[SEL_REC_SIZE_OFFSET];
            recordsNumber = proprietaryInformation[SEL_NUM_REC_OFFSET];
        }

        byte[] accessConditions = new byte[SEL_AC_LENGTH];
        System.arraycopy(proprietaryInformation, SEL_AC_OFFSET, accessConditions, 0, SEL_AC_LENGTH);

        byte[] keyIndexes = new byte[SEL_NKEY_LENGTH];
        System.arraycopy(proprietaryInformation, SEL_NKEY_OFFSET, keyIndexes, 0, SEL_NKEY_LENGTH);

        byte dfStatus = proprietaryInformation[SEL_DF_STATUS_OFFSET];

        short sharedReference =
                (short) (((proprietaryInformation[SEL_DATA_REF_OFFSET] << 8) & 0xff00)
                        | (proprietaryInformation[SEL_DATA_REF_OFFSET + 1] & 0x00ff));

        short lid = (short) (((proprietaryInformation[SEL_LID_OFFSET] << 8) & 0xff00)
                | (proprietaryInformation[SEL_LID_OFFSET + 1] & 0x00ff));

        return FileHeader.builder()//
                .lid(lid)//
                .recordsNumber(recordsNumber)//
                .recordSize(recordSize)//
                .type(fileType)//
                .accessConditions(Arrays.copyOf(accessConditions, accessConditions.length))//
                .keyIndexes(Arrays.copyOf(keyIndexes, keyIndexes.length))//
                .dfStatus(dfStatus)//
                .sharedReference(sharedReference)//
                .build();
    }

    /**
     * (package-private)<br>
     * Fills the CalypsoPo with the PO's response to a single command
     *
     * @param calypsoPo the {@link CalypsoPo} object to fill with the provided response from the PO
     * @param commandBuilder the builder of the command that get the response
     * @param apduResponse the APDU response returned by the PO to the command
     */
    static AbstractPoResponseParser updateCalypsoPo(CalypsoPo calypsoPo,
            AbstractPoCommandBuilder<? extends AbstractPoResponseParser> commandBuilder,
            ApduResponse apduResponse) throws CalypsoPoCommandException {
        switch (commandBuilder.getCommandRef()) {
            case READ_RECORDS:
                return updateCalypsoPoReadRecords(calypsoPo, (ReadRecordsCmdBuild) commandBuilder,
                        apduResponse);
            case SELECT_FILE:
                return updateCalypsoPoSelectFile(calypsoPo, (SelectFileCmdBuild) commandBuilder,
                        apduResponse);
            case UPDATE_RECORD:
                return updateCalypsoPoUpdateRecord(calypsoPo, (UpdateRecordCmdBuild) commandBuilder,
                        apduResponse);
            case WRITE_RECORD:
                return updateCalypsoPoWriteRecord(calypsoPo, (WriteRecordCmdBuild) commandBuilder,
                        apduResponse);
            case APPEND_RECORD:
                return updateCalypsoPoAppendRecord(calypsoPo, (AppendRecordCmdBuild) commandBuilder,
                        apduResponse);
            case DECREASE:
                return updateCalypsoPoDecrease(calypsoPo, (DecreaseCmdBuild) commandBuilder,
                        apduResponse);
            case INCREASE:
                return updateCalypsoPoIncrease(calypsoPo, (IncreaseCmdBuild) commandBuilder,
                        apduResponse);
            case OPEN_SESSION_10:
            case OPEN_SESSION_24:
            case OPEN_SESSION_31:
            case OPEN_SESSION_32:
                return updateCalypsoPoOpenSession(calypsoPo,
                        (AbstractOpenSessionCmdBuild) commandBuilder, apduResponse);
            case CHANGE_KEY:
            case GET_DATA_FCI:
            case GET_DATA_TRACE:
                throw new IllegalStateException("Shouldn't happen for now!");
            default:
                throw new IllegalStateException("Unknown command reference.");
        }
    }

    /**
     * (package-private)<br>
     * Fills the CalypsoPo with the PO's responses to a list of commands
     *
     * @param calypsoPo the {@link CalypsoPo} object to fill with the provided response from the PO
     * @param commandBuilders the list of builders that get the responses
     * @param apduResponses the APDU responses returned by the PO to all commands
     */
    static void updateCalypsoPo(CalypsoPo calypsoPo,
            List<AbstractPoCommandBuilder<? extends AbstractPoResponseParser>> commandBuilders,
            List<ApduResponse> apduResponses) throws CalypsoPoCommandException {
        Iterator<ApduResponse> responseIterator = apduResponses.iterator();

        if (commandBuilders != null && !commandBuilders.isEmpty()) {
            for (AbstractPoCommandBuilder<? extends AbstractPoResponseParser> commandBuilder : commandBuilders) {
                ApduResponse apduResponse = responseIterator.next();
                updateCalypsoPo(calypsoPo, commandBuilder, apduResponse);
            }
        }
    }

    /**
     * Create a Read Records command builder for the provided arguments
     *
     * @param poClass the class of the PO
     * @param sfi the SFI of the EF to read
     * @param recordNumber the record number to read
     * @return a {@link ReadRecordsCmdBuild} object
     */
    static ReadRecordsCmdBuild prepareReadRecordFile(PoClass poClass, byte sfi, int recordNumber) {
        Assert.getInstance() //
                .isInRange((int) sfi, CalypsoPoUtils.SFI_MIN, CalypsoPoUtils.SFI_MAX, "sfi") //
                .isInRange(recordNumber, CalypsoPoUtils.NB_REC_MIN, CalypsoPoUtils.NB_REC_MAX,
                        "recordNumber");

        return new ReadRecordsCmdBuild(poClass, sfi, recordNumber,
                ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 0);
    }

    /**
     * Create a Select File command builder for the provided LID
     *
     * @param poClass the class of the PO
     * @param lid the LID of the EF to select
     * @return a {@link SelectFileCmdBuild} object
     */
    static SelectFileCmdBuild prepareSelectFile(PoClass poClass, byte[] lid) {
        Assert.getInstance().notNull(lid, "lid")//
                .isEqual(lid.length, 2, "lid");

        return new SelectFileCmdBuild(poClass, lid);
    }

    /**
     * Create a Select File command builder for the provided select control
     *
     * @param poClass the class of the PO
     * @param selectControl provides the navigation case: FIRST, NEXT or CURRENT
     * @return a {@link SelectFileCmdBuild} object
     */
    static SelectFileCmdBuild prepareSelectFile(PoClass poClass, SelectFileControl selectControl) {
        return new SelectFileCmdBuild(poClass, selectControl);
    }
}
