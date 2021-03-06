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

import static org.eclipse.keyple.calypso.transaction.PoTransaction.SessionSetting.AccessLevel;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.keyple.calypso.SelectFileControl;
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException;
import org.eclipse.keyple.calypso.command.sam.exception.CalypsoSamCommandException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoAtomicTransactionException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoAuthenticationNotVerifiedException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoCloseSecureSessionException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoIOException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionIllegalStateException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoSessionAuthenticationException;
import org.eclipse.keyple.calypso.transaction.exception.CalypsoUnauthorizedKvcException;
import org.eclipse.keyple.core.seproxy.ChannelControl;
import org.eclipse.keyple.core.seproxy.MultiSeRequestProcessing;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderIOException;
import org.eclipse.keyple.core.seproxy.message.AnswerToReset;
import org.eclipse.keyple.core.seproxy.message.ApduRequest;
import org.eclipse.keyple.core.seproxy.message.ApduResponse;
import org.eclipse.keyple.core.seproxy.message.ProxyReader;
import org.eclipse.keyple.core.seproxy.message.SeRequest;
import org.eclipse.keyple.core.seproxy.message.SeResponse;
import org.eclipse.keyple.core.seproxy.message.SelectionStatus;
import org.eclipse.keyple.core.seproxy.protocol.TransmissionMode;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


public class PoTransactionTest {
    // The default KIF values for personalization, loading and debiting
    final byte DEFAULT_KIF_PERSO = (byte) 0x21;
    final byte DEFAULT_KIF_LOAD = (byte) 0x27;
    final byte DEFAULT_KIF_DEBIT = (byte) 0x30;
    // The default key record number values for personalization, loading and debiting
    // The actual value should be adjusted.
    final byte DEFAULT_KEY_RECORD_NUMBER_PERSO = (byte) 0x01;
    final byte DEFAULT_KEY_RECORD_NUMBER_LOAD = (byte) 0x02;
    final byte DEFAULT_KEY_RECORD_NUMBER_DEBIT = (byte) 0x03;
    private SeReader poReader;
    private PoTransaction poTransaction;
    private SamResource samResource;

    private final Map<String, String> poCommandsTestSet = new HashMap<String, String>();
    private final Map<String, String> samCommandsTestSet = new HashMap<String, String>();
    final String FCI_REV10 =
            "6F228408315449432E494341A516BF0C13C708   0000000011223344 5307060A01032003119000";
    final String FCI_REV24 =
            "6F2A8410A0000004040125090101000000000000A516BF0C13C708 0000000011223344 53070A2E11420001019000";
    final String FCI_REV31 =
            "6F238409315449432E49434131A516BF0C13C708 0000000011223344 53070A3C23121410019000";

    @Before
    public void setUp() throws Exception {
        poCommandsTestSet.clear();
        samCommandsTestSet.clear();
        poReader = createMockReader("PO", TransmissionMode.CONTACTLESS, poCommandsTestSet);
        SeReader samReader = createMockReader("SAM", TransmissionMode.CONTACTS, samCommandsTestSet);
        CalypsoSam calypsoSam = createCalypsoSam();

        samResource = new SamResource(samReader, calypsoSam);
    }

    /* Standard opening with two records read */
    @Test(expected = CalypsoPoTransactionIllegalStateException.class)
    public void testProcessOpening_no_sam_resource() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);

        // PoTransaction without PoSecuritySettings
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "CCBBAA99887766554433221100FFEEDDCCBBAA998877665544332211009000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        // should raise an exception
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    /* Standard opening with two records read */
    @Test(expected = CalypsoPoTransactionIllegalStateException.class)
    public void testProcessOpening_read_reopen() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "CCBBAA99887766554433221100FFEEDDCCBBAA998877665544332211009000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent());
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("CCBBAA99887766554433221100FFEEDDCCBBAA99887766554433221100"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent());
        Assert.assertTrue(calypsoPoRev31.isDfRatified());
        // expected exception: session is already open
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    /* Standard opening, DF not previously ratified */
    @Test
    public void testProcessOpening_df_not_ratified() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1 / DF not ratified (01)
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980130791D71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "CCBBAA99887766554433221100FFEEDDCCBBAA998877665544332211009000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent());
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("CCBBAA99887766554433221100FFEEDDCCBBAA99887766554433221100"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent());
        Assert.assertFalse(calypsoPoRev31.isDfRatified());
    }

    /* Standard opening with 1 multiple records read */
    @Test
    public void testProcessOpening_read_multiple_records() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();
        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Read Recordsfi 7 / rec 3 and 4
        poCommandsTestSet.put("00B2033D3E",
                "031D7333333333333333333333333333333333333333333333333333333333041D74444444444444444444444444444444444444444444444444444444449000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 3, 2, 29);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7333333333333333333333333333333333333333333333333333333333"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(3));
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7444444444444444444444444444444444444444444444444444444444"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(4));
    }

    /* Standard opening but KVC is not present authorized list */
    @Test(expected = CalypsoUnauthorizedKvcException.class)
    public void testProcessOpening_kvc_not_authorized() throws CalypsoPoCommandException,
            CalypsoSamCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);

        List<Byte> authorizedKvc = new ArrayList<Byte>();
        authorizedKvc.add((byte) 0x79);

        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .sessionAuthorizedKvcList(authorizedKvc).build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030781D71111111111111111111111111111111111111111111111111111111119000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        // an exception is expected
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    /*
     * Buffer overflow limit in atomic mode (counter in bytes): session buffer size = 430 b,
     * consumed size 430 b
     */
    @Test
    public void testProcessOpening_session_buffer_limit() throws CalypsoPoCommandException,
            CalypsoSamCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();
        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 7 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 8 rec 1 10 bytes
        poCommandsTestSet.put("00DC01440433221100", "9000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil.fromHex("33221100"));
        // ATOMIC transaction should be ok (430 / 430 bytes consumed)
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        Assert.assertTrue(true);
    }

    /*
     * Buffer overflowed in atomic mode (counter in bytes): session buffer size = 430 b, consumed
     * size 431 b
     */
    @Test(expected = CalypsoAtomicTransactionException.class)
    public void testProcessOpening_session_buffer_overflow_bytes_counter()
            throws CalypsoPoCommandException, CalypsoSamCommandException,
            CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();
        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 8 rec 1 10 bytes
        poCommandsTestSet.put("00DC0144054433221100", "9000");


        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // 5 additional bytes (11 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));

        // expected exception: session buffer overflow
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    /*
     * Buffer overflow limit in atomic mode (counter in operations): session buffer size = 6 op,
     * consumed 6 op
     */
    @Test
    public void testProcessOpening_session_buffer_limit_operations_counter()
            throws CalypsoPoCommandException, CalypsoSamCommandException,
            CalypsoPoTransactionException {
        // Select Diversifier
        CalypsoPo calypsoPoRev24 = createCalypsoPo(FCI_REV24);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();
        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev24), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V2.4 + read sfi 7 / rec 1
        poCommandsTestSet.put("948A8B3804C1C2C3C400",
                "79030D307124B928480805CBABAE30001240800000000000000000000000000000009000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "94DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        // 6 x update (29 b) = 6 operations consumed in the session buffer
        for (int i = 0; i < 6; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // ATOMIC transaction should be ok (6 / 6 operations consumed)
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        Assert.assertTrue(true);
    }

    /*
     * Buffer overflow limit in atomic mode (counter in operations): session buffer size = 6 op,
     * consumed 7 op
     */
    @Test(expected = CalypsoAtomicTransactionException.class)
    public void testProcessOpening_session_buffer_overflow_operations_counter()
            throws CalypsoPoCommandException, CalypsoSamCommandException,
            CalypsoPoTransactionException {
        // Select Diversifier
        CalypsoPo calypsoPoRev24 = createCalypsoPo(FCI_REV24);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();
        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev24), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V2.4 + read sfi 7 / rec 1
        poCommandsTestSet.put("948A8B3804C1C2C3C400",
                "79030D307124B928480805CBABAE30001240800000000000000000000000000000009000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "94DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        // 7 x update (29 b) = 7 operations consumed in the session buffer
        for (int i = 0; i < 7; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // ATOMIC transaction should be ko (7 / 6 operations consumed)
        // expected exception: session buffer overflow
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    /*
     * Buffer overflowed in multiple mode (counter in bytes): session buffer size = 430 b, consumed
     * size 431 b
     */
    @Test
    public void testProcessOpening_session_buffer_overflow_bytes_counter_mulitple_mode()
            throws CalypsoPoCommandException, CalypsoSamCommandException,
            CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .sessionModificationMode(
                                PoTransaction.SessionSetting.ModificationMode.MULTIPLE)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put(
                "808A00FF273079030490980030791D7111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put(
                "808C00002200DC01441D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 8 rec 1 10 bytes
        poCommandsTestSet.put("00DC0144054433221100", "9000");
        // Close Secure Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");


        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // 5 additional bytes (11 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));
        // ATOMIC transaction should be ok (430 / 431 bytes consumed)
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        Assert.assertTrue(true);
    }

    /* standard process Po commands */
    @Test
    public void testProcessPoCommands_nominal_case()
            throws CalypsoPoCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 7 / rec 1
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 7 / rec 3 and 4
        poCommandsTestSet.put("00B202453E",
                "031D" + "7333333333333333333333333333333333333333333333333333333333" + "041D"
                        + "7444444444444444444444444444444444444444444444444444444444" + "9000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 2, 2, 29);
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("8111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent(1));
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(1));
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7333333333333333333333333333333333333333333333333333333333"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent(3));
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7444444444444444444444444444444444444444444444444444444444"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent(4));
    }

    /* standard process Po commands: session open before */
    @Test(expected = CalypsoPoTransactionIllegalStateException.class)
    public void testProcessPoCommands_session_open() throws CalypsoPoCommandException,
            CalypsoPoTransactionException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .sessionModificationMode(
                                PoTransaction.SessionSetting.ModificationMode.MULTIPLE)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        // PoTransaction while a session is open
        // expected exception: a session is open
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);
    }

    /* No session open */
    @Test(expected = CalypsoPoTransactionIllegalStateException.class)
    public void testProcessPoCommandsInSession_no_session_open()
            throws CalypsoPoTransactionException, CalypsoPoCommandException,
            CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        // expected exception: no session is open
        poTransaction.processPoCommandsInSession();
    }

    /* Standard processPoCommandsInSession */
    @Test
    public void testProcessPoCommandsInSession_nominal_case() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        // PoTransaction after a session is open
        poTransaction.processPoCommandsInSession();
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(1));
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("8111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent(1));
    }

    /* processClosing no session open */
    @Test(expected = CalypsoPoTransactionIllegalStateException.class)
    public void testProcessClosing_no_session_open() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        // expected exception: no session is open
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
    }

    /* Standard processClosing - default ratification */
    @Test
    public void testProcessClosing_nominal_case() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put(
                "808A00FF273079030490980030791D7111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000500B2018400", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C000024001122000000000000000000000000000000000000000000000000000000000000009000",
                "9000");
        // Digest update
        samCommandsTestSet.put("808C00000500B2018C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C000024002211000000000000000000000000000000000000000000000000000000000000009000",
                "9000");
        // Digest update
        samCommandsTestSet.put("808C0000080030018003000064", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000050010BE9000", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000080032018803000064", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000050022759000", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000900DC013C0400112233", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000900D201440444556677", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000900E20048048899AABB", "9000");

        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Read Record SFI 0x10 counter
        poCommandsTestSet.put("00B2018400",
                "001122000000000000000000000000000000000000000000000000000000000000009000");
        // Read Record SFI 0x11 counter
        poCommandsTestSet.put("00B2018C00",
                "002211000000000000000000000000000000000000000000000000000000000000009000");
        // Update Record SFI 8 rec 1 10 bytes
        poCommandsTestSet.put("00DC01440433221100", "9000");
        // Decrease SFI 10
        poCommandsTestSet.put("003001800300006400", "0010BE9000");
        // Decrease SFI 11
        poCommandsTestSet.put("003201880300006400", "0022759000");
        // Update Record SFI 7
        poCommandsTestSet.put("00DC013C0400112233", "9000");
        // Write Record SFI 8
        poCommandsTestSet.put("00D201440444556677", "9000");
        // Append Record SFI 9
        poCommandsTestSet.put("00E20048048899AABB", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");
        // Ratification command
        poCommandsTestSet.put("00B2000000", "6B00");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x10, 1);
        poTransaction.prepareReadRecordFile((byte) 0x11, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareDecrease((byte) 0x10, (byte) 1, 100);
        poTransaction.prepareIncrease((byte) 0x11, (byte) 1, 100);
        poTransaction.prepareUpdateRecord((byte) 0x07, (byte) 1, ByteArrayUtil.fromHex("00112233"));
        poTransaction.prepareWriteRecord((byte) 0x08, (byte) 1, ByteArrayUtil.fromHex("44556677"));
        poTransaction.prepareAppendRecord((byte) 0x09, ByteArrayUtil.fromHex("8899AABB"));

        // PoTransaction after a session is open
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
        Assert.assertEquals(0x1122 - 100,
                calypsoPoRev31.getFileBySfi((byte) 0x10).getData().getContentAsCounterValue(1));
        Assert.assertEquals(0x2211 + 100,
                calypsoPoRev31.getFileBySfi((byte) 0x11).getData().getContentAsCounterValue(1));
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("00112233"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(1));
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("44556677"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent(1));
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("8899AABB"),
                calypsoPoRev31.getFileBySfi((byte) 0x09).getData().getContent(1));
    }

    /* processClosing - PO fail on closing */
    @Test(expected = CalypsoPoCloseSecureSessionException.class)
    public void testProcessClosing_po_close_fail() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Read record SFI 7 rec 1
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Close Session - status error
        poCommandsTestSet.put("008E8000041122334400", "6988");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);

        // PoTransaction after a session is open
        // should raise a CalypsoPoCloseSecureSessionException
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
    }

    /* processClosing - SAM authentication fail on closing */
    @Test(expected = CalypsoSessionAuthenticationException.class)
    public void testProcessClosing_sam_authenticate_fail() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "6988");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Read record SFI 7 rec 1
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);

        // PoTransaction after a session is open
        // should raise a CalypsoSessionAuthenticationException
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
    }


    /* processClosing - SAM IO error while authenticating */
    @Test(expected = CalypsoAuthenticationNotVerifiedException.class)
    public void testProcessClosing_sam_io_error_authenticating()
            throws CalypsoPoTransactionException, CalypsoPoCommandException,
            CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        // EMPTY

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Read record SFI 7 rec 1
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);

        // PoTransaction after a session is open
        // should raise a CalypsoAuthenticationNotVerifiedException
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
    }

    /*
     * Buffer overflow limit in atomic mode (counter in bytes): session buffer size = 430 b,
     * consumed size 430 b
     */
    @Test
    public void testProcessClosing_session_buffer_limit() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00002200DC01441D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000900DC013C0433221100", "9000");


        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 7 rec 1 4 bytes
        poCommandsTestSet.put("00DC013C0433221100", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x07, (byte) 1, ByteArrayUtil.fromHex("33221100"));

        // PoTransaction after a session is open
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("33221100"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent(1));
    }

    /*
     * Buffer overflowed in atomic mode (counter in bytes): session buffer size = 430 b, consumed
     * size 431 b
     */
    @Test
    public void testProcessClosing_session_buffer_overflowed() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00002200DC013C1D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000900DC013C0433221100", "9000");


        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC013C1D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 7 rec 1 5 bytes
        poCommandsTestSet.put("00DC013C054433221100", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));

        try {
            // PoTransaction after a session is open
            poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
        } catch (CalypsoAtomicTransactionException ex) {
            // expected exception: session buffer overflow
            return;
        }
        Assert.fail();
    }

    /*
     * Buffer overflowed in multiple mode (counter in bytes): session buffer size = 430 b, consumed
     * size 431 b
     */
    @Test
    public void testProcessClosing_session_buffer_overflow_multiple_mode()
            throws CalypsoPoTransactionException, CalypsoPoCommandException,
            CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .sessionModificationMode(
                                PoTransaction.SessionSetting.ModificationMode.MULTIPLE)//
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00002200DC01441D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00001F71111111111111111111111111111111111111111111111111111111119000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000900DC013C0433221100", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000A00DC013C054433221100", "9000");

        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 7 rec 1 5 bytes
        poCommandsTestSet.put("00DC013C054433221100", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 12 x update (29 b) = 12 x (29 + 6) = 420 consumed in the session buffer
        for (int i = 0; i < 12; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x07, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));

        // PoTransaction after a session is open
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);

        Assert.assertTrue(true);
    }

    /* Standard processClosing - close not ratified */
    @Test
    public void testProcessClosing_nominal_case_close_not_ratified()
            throws CalypsoPoTransactionException, CalypsoPoCommandException,
            CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .ratificationMode(
                                PoTransaction.SessionSetting.RatificationMode.CLOSE_NOT_RATIFIED)//
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put(
                "808A00FF273079030490980030791D7111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Close Session not ratified
        poCommandsTestSet.put("008E0000041122334400", "556677889000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);

        Assert.assertTrue(true);
    }

    /* Session buffer overflow in atomic mode: the overflow happens at closing */
    @Test
    public void testTransaction_buffer_overflow_atomic() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00002200DC013C1D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00001F71111111111111111111111111111111111111111111111111111111119000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000900DC013C0433221100", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000A00DC013C054433221100", "9000");

        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 7 rec 1 5 bytes
        poCommandsTestSet.put("00DC013C054433221100", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        // 4 x update (29 b) = 4 x (29 + 6) = 140 consumed in the session buffer
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 4 x update (29 b) = 4 x (29 + 6) = 140 consumed in the session buffer
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        poTransaction.processPoCommandsInSession();

        // 5 x update (29 b) = 5 x (29 + 6) = 140 consumed in the session buffer
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x07, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));

        try {
            // PoTransaction after a session is open
            poTransaction.processClosing(ChannelControl.CLOSE_AFTER);
        } catch (CalypsoAtomicTransactionException ex) {
            // expected exception: buffer overflow
            return;
        }
        Assert.fail();
    }

    /* Session buffer overflow in multiple mode: the overflow happens and is handled at closing */
    @Test
    public void testTransaction_buffer_overflow_multiple() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource) //
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT) //
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)//
                        .sessionModificationMode(
                                PoTransaction.SessionSetting.ModificationMode.MULTIPLE)//
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);

        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");
        // Digest Init
        samCommandsTestSet.put("808A00FF0A30790304909800307900", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2014400", "9000");
        // Digest Update
        samCommandsTestSet.put("808C0000029000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000500B2013C00", "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00002200DC01441D8111111111111111111111111111111111111111111111111111111111",
                "9000");
        // Digest update
        samCommandsTestSet.put(
                "808C00001F71111111111111111111111111111111111111111111111111111111119000", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000900DC013C0433221100", "9000");
        // Digest Update
        samCommandsTestSet.put("808C00000A00DC013C054433221100", "9000");

        // Digest Close
        samCommandsTestSet.put("808E000004", "112233449000");
        // Digest Authenticate
        samCommandsTestSet.put("808200000455667788", "9000");

        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Reader record SFI 7 1 rec
        poCommandsTestSet.put("00B2013C00",
                "71111111111111111111111111111111111111111111111111111111119000");
        // Update Record SFI 8 rec 1 29 bytes
        poCommandsTestSet.put(
                "00DC01441D8111111111111111111111111111111111111111111111111111111111", "9000");
        // Update Record SFI 7 rec 1 5 bytes
        poCommandsTestSet.put("00DC013C054433221100", "9000");
        // Close Session
        poCommandsTestSet.put("008E8000041122334400", "556677889000");

        // 4 x update (29 b) = 4 x (29 + 6) = 140 consumed in the session buffer
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);

        // add additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        // 24 x update (29 b) = 24 x (29 + 6) = 840 consumed in the session buffer
        // force multiple cycles
        for (int i = 0; i < 24; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // insert additional non modifying commands (should not affect the session buffer)
        for (int i = 0; i < 4; i++) {
            poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        }
        poTransaction.processPoCommandsInSession();

        // 24 x update (29 b) = 24 x (29 + 6) = 840 consumed in the session buffer
        // force multiple cycles
        for (int i = 0; i < 24; i++) {
            poTransaction.prepareUpdateRecord((byte) 0x08, (byte) 1, ByteArrayUtil
                    .fromHex("8111111111111111111111111111111111111111111111111111111111"));
        }
        // 4 additional bytes (10 b consumed)
        poTransaction.prepareUpdateRecord((byte) 0x07, (byte) 1,
                ByteArrayUtil.fromHex("4433221100"));

        // PoTransaction after a session is open
        poTransaction.processClosing(ChannelControl.CLOSE_AFTER);

        Assert.assertTrue(true);
    }

    /* open, cancel and reopen */
    @Test
    public void testProcessCancel_open_cancel_open() throws CalypsoPoTransactionException,
            CalypsoPoCommandException, CalypsoSamCommandException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        PoSecuritySettings poSecuritySettings =
                new PoSecuritySettings.PoSecuritySettingsBuilder(samResource)//
                        .sessionDefaultKif(AccessLevel.SESSION_LVL_DEBIT, DEFAULT_KIF_DEBIT)//
                        .sessionDefaultKeyRecordNumber(AccessLevel.SESSION_LVL_DEBIT,
                                DEFAULT_KEY_RECORD_NUMBER_DEBIT)
                        .build();

        poTransaction =
                new PoTransaction(new PoResource(poReader, calypsoPoRev31), poSecuritySettings);
        // Select Diversifier
        samCommandsTestSet.put("80140000080000000011223344", "9000");
        // Get Challenge
        samCommandsTestSet.put("8084000004", "C1C2C3C49000");

        // Open Secure Session V3.1 + read sfi 7 / rec 1
        poCommandsTestSet.put("008A0B3904C1C2C3C400",
                "030490980030791D71111111111111111111111111111111111111111111111111111111119000");
        // Open Secure Session V3.1
        poCommandsTestSet.put("008A030104C1C2C3C400", "03049098003079009000");
        // Read Recordsfi 8 / rec 1
        poCommandsTestSet.put("00B2014400",
                "81111111111111111111111111111111111111111111111111111111119000");
        // Abort session
        poCommandsTestSet.put("008E000000", "9000");

        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.prepareReadRecordFile((byte) 0x08, 1);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("7111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContent());
        Assert.assertArrayEquals(
                ByteArrayUtil.fromHex("8111111111111111111111111111111111111111111111111111111111"),
                calypsoPoRev31.getFileBySfi((byte) 0x08).getData().getContent());
        Assert.assertTrue(calypsoPoRev31.isDfRatified());
        poTransaction.processCancel(ChannelControl.KEEP_OPEN);
        poTransaction.processOpening(AccessLevel.SESSION_LVL_DEBIT);
    }

    @Test
    public void testPrepareSelectFile_select_control()
            throws CalypsoPoCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        poCommandsTestSet.put("00A4090002000000",
                "85170001000000101000000103010100777879616770003F009000");
        poCommandsTestSet.put("00A4020002000000",
                "85170204021D011F00000001010101003F02000000000000029000");
        poCommandsTestSet.put("00A4020202000000",
                "85170304021D010110000001020101003F03000000000000039000");

        poTransaction.prepareSelectFile(SelectFileControl.CURRENT_DF);
        poTransaction.prepareSelectFile(SelectFileControl.FIRST_EF);
        poTransaction.prepareSelectFile(SelectFileControl.NEXT_EF);
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);
        DirectoryHeader directoryHeader = calypsoPoRev31.getDirectoryHeader();
        FileHeader fileHeader1 = calypsoPoRev31.getFileByLid((short) 0x02).getHeader();
        FileHeader fileHeader2 = calypsoPoRev31.getFileByLid((short) 0x03).getHeader();
        System.out.println(directoryHeader);
        System.out.println(fileHeader1);
        System.out.println(fileHeader2);

        Assert.assertEquals((short) 0x3F00, directoryHeader.getLid());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("10100000"),
                directoryHeader.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01030101"),
                directoryHeader.getKeyIndexes());
        Assert.assertEquals((short) 0x00, directoryHeader.getDfStatus());
        Assert.assertEquals((byte) 0x61, directoryHeader.getKif(AccessLevel.SESSION_LVL_PERSO));
        Assert.assertEquals((byte) 0x67, directoryHeader.getKif(AccessLevel.SESSION_LVL_LOAD));
        Assert.assertEquals((byte) 0x70, directoryHeader.getKif(AccessLevel.SESSION_LVL_DEBIT));
        Assert.assertEquals((byte) 0x77, directoryHeader.getKvc(AccessLevel.SESSION_LVL_PERSO));
        Assert.assertEquals((byte) 0x78, directoryHeader.getKvc(AccessLevel.SESSION_LVL_LOAD));
        Assert.assertEquals((byte) 0x79, directoryHeader.getKvc(AccessLevel.SESSION_LVL_DEBIT));

        Assert.assertEquals((short) 0x0002, fileHeader1.getLid());
        Assert.assertEquals(1, fileHeader1.getRecordsNumber());
        Assert.assertEquals(29, fileHeader1.getRecordSize());
        Assert.assertEquals(FileHeader.FileType.LINEAR, fileHeader1.getType());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("1F000000"),
                fileHeader1.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01010101"), fileHeader1.getKeyIndexes());
        Assert.assertEquals((short) 0x00, fileHeader1.getDfStatus());
        Assert.assertEquals(Short.valueOf((short) 0x3F02), fileHeader1.getSharedReference());

        Assert.assertEquals((short) 0x0003, fileHeader2.getLid());
        Assert.assertEquals(1, fileHeader2.getRecordsNumber());
        Assert.assertEquals(29, fileHeader2.getRecordSize());
        Assert.assertEquals(FileHeader.FileType.LINEAR, fileHeader2.getType());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01100000"),
                fileHeader2.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01020101"), fileHeader2.getKeyIndexes());
        Assert.assertEquals((short) 0x00, fileHeader2.getDfStatus());
        Assert.assertEquals(Short.valueOf((short) 0x3F03), fileHeader2.getSharedReference());
    }

    @Test
    public void testPrepareSelectFile_lid()
            throws CalypsoPoCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        poCommandsTestSet.put("00A40900023F0000",
                "85170001000000101000000103010100777879616770003F009000");
        poCommandsTestSet.put("00A4090002000200",
                "85170204021D011F00000001010101003F02000000000000029000");
        poCommandsTestSet.put("00A4090002000300",
                "85170304021D010110000001020101003F03000000000000039000");

        poTransaction.prepareSelectFile(ByteArrayUtil.fromHex("3F00"));
        poTransaction.prepareSelectFile(ByteArrayUtil.fromHex("0002"));
        poTransaction.prepareSelectFile(ByteArrayUtil.fromHex("0003"));
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);

        DirectoryHeader directoryHeader = calypsoPoRev31.getDirectoryHeader();
        ElementaryFile file1 = calypsoPoRev31.getFileByLid((short) 0x02);
        ElementaryFile file2 = calypsoPoRev31.getFileByLid((short) 0x03);
        byte sfi1 = file1.getSfi();
        byte sfi2 = file2.getSfi();
        System.out.println(directoryHeader);
        System.out.println(file1);
        System.out.println(file2);

        Assert.assertEquals(file1, calypsoPoRev31.getFileBySfi(sfi1));
        Assert.assertEquals(file2, calypsoPoRev31.getFileBySfi(sfi2));

        Assert.assertEquals((short) 0x3F00, directoryHeader.getLid());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("10100000"),
                directoryHeader.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01030101"),
                directoryHeader.getKeyIndexes());
        Assert.assertEquals((short) 0x00, directoryHeader.getDfStatus());
        Assert.assertEquals((byte) 0x61, directoryHeader.getKif(AccessLevel.SESSION_LVL_PERSO));
        Assert.assertEquals((byte) 0x67, directoryHeader.getKif(AccessLevel.SESSION_LVL_LOAD));
        Assert.assertEquals((byte) 0x70, directoryHeader.getKif(AccessLevel.SESSION_LVL_DEBIT));
        Assert.assertEquals((byte) 0x77, directoryHeader.getKvc(AccessLevel.SESSION_LVL_PERSO));
        Assert.assertEquals((byte) 0x78, directoryHeader.getKvc(AccessLevel.SESSION_LVL_LOAD));
        Assert.assertEquals((byte) 0x79, directoryHeader.getKvc(AccessLevel.SESSION_LVL_DEBIT));

        FileHeader fileHeader1 = file1.getHeader();
        Assert.assertEquals((short) 0x0002, fileHeader1.getLid());
        Assert.assertEquals(1, fileHeader1.getRecordsNumber());
        Assert.assertEquals(29, fileHeader1.getRecordSize());
        Assert.assertEquals(FileHeader.FileType.LINEAR, fileHeader1.getType());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("1F000000"),
                fileHeader1.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01010101"), fileHeader1.getKeyIndexes());
        Assert.assertEquals((short) 0x00, fileHeader1.getDfStatus());
        Assert.assertEquals(Short.valueOf((short) 0x3F02), fileHeader1.getSharedReference());

        FileHeader fileHeader2 = file2.getHeader();
        Assert.assertEquals((short) 0x0003, fileHeader2.getLid());
        Assert.assertEquals(1, fileHeader2.getRecordsNumber());
        Assert.assertEquals(29, fileHeader2.getRecordSize());
        Assert.assertEquals(FileHeader.FileType.LINEAR, fileHeader2.getType());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01100000"),
                fileHeader2.getAccessConditions());
        Assert.assertArrayEquals(ByteArrayUtil.fromHex("01020101"), fileHeader2.getKeyIndexes());
        Assert.assertEquals((short) 0x00, fileHeader2.getDfStatus());
        Assert.assertEquals(Short.valueOf((short) 0x3F03), fileHeader2.getSharedReference());
    }

    @Test
    public void testPrepareReadCounterFile()
            throws CalypsoPoCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));

        poCommandsTestSet.put("00B2013C06", "A55AA5 5AA55A 9000");

        // read counter 2 but counter 1 will be read too
        poTransaction.prepareReadCounterFile((byte) 0x07, 2);
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);
        Assert.assertEquals(0xA55AA5,
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContentAsCounterValue(1));
        Assert.assertEquals(0x5AA55A,
                calypsoPoRev31.getFileBySfi((byte) 0x07).getData().getContentAsCounterValue(2));
    }

    @Test(expected = CalypsoPoIOException.class)
    public void testPoIoException()
            throws CalypsoPoCommandException, CalypsoPoTransactionException {
        CalypsoPo calypsoPoRev31 = createCalypsoPo(FCI_REV31);
        poTransaction = new PoTransaction(new PoResource(poReader, calypsoPoRev31));
        poTransaction.prepareReadRecordFile((byte) 0x07, 1);
        poTransaction.processPoCommands(ChannelControl.KEEP_OPEN);
    }

    @Test
    public void testAccessLevel() {
        Assert.assertEquals("perso", AccessLevel.SESSION_LVL_PERSO.getName());
        Assert.assertEquals("load", AccessLevel.SESSION_LVL_LOAD.getName());
        Assert.assertEquals("debit", AccessLevel.SESSION_LVL_DEBIT.getName());
        Assert.assertEquals((byte) 0x01, AccessLevel.SESSION_LVL_PERSO.getSessionKey());
        Assert.assertEquals((byte) 0x02, AccessLevel.SESSION_LVL_LOAD.getSessionKey());
        Assert.assertEquals((byte) 0x03, AccessLevel.SESSION_LVL_DEBIT.getSessionKey());
    }

    private CalypsoPo createCalypsoPo(String FCI) {
        SeResponse selectionData = new SeResponse(true, false,
                new SelectionStatus(null, new ApduResponse(ByteArrayUtil.fromHex(FCI), null), true),
                null);
        return new CalypsoPo(selectionData, TransmissionMode.CONTACTLESS);
    }

    private CalypsoSam createCalypsoSam() {
        final String ATR1 = "3B3F9600805A0080C120000012345678829000";

        SelectionStatus selectionStatus =
                new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR1)), null, true);
        return new CalypsoSam(new SeResponse(true, true, selectionStatus, null),
                TransmissionMode.CONTACTS);
    }

    private ProxyReader createMockReader(final String name, TransmissionMode transmissionMode,
            final Map<String, String> commandTestSet) throws KeypleReaderIOException {

        // configure mock native reader
        ProxyReader mockReader = Mockito.spy(ProxyReader.class);
        doReturn(name).when(mockReader).getName();
        doReturn(transmissionMode).when(mockReader).getTransmissionMode();

        doAnswer(new Answer<List<SeResponse>>() {
            @Override
            public List<SeResponse> answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                List<SeRequest> seRequests = (List<SeRequest>) args[0];
                for (SeRequest seRequest : seRequests) {
                    System.out.println("SeRequest " + seRequest.toString());
                }
                return null;
            }
        }).when(mockReader).transmitSeRequests(ArgumentMatchers.<SeRequest>anyList(),
                any(MultiSeRequestProcessing.class), any(ChannelControl.class));

        doAnswer(new Answer<SeResponse>() {
            @Override
            public SeResponse answer(InvocationOnMock invocation) throws KeypleReaderIOException {
                Object[] args = invocation.getArguments();
                SeRequest seRequest = (SeRequest) args[0];
                List<ApduRequest> apduRequests = seRequest.getApduRequests();
                List<ApduResponse> apduResponses = new ArrayList<ApduResponse>();
                try {
                    for (ApduRequest apduRequest : apduRequests) {
                        ApduResponse apduResponse = getResponses(name, commandTestSet, apduRequest);
                        apduResponses.add(apduResponse);
                        System.out.println("Request=" + apduRequest.toString() + ", Response="
                                + apduResponse.toString());
                    }
                } catch (KeypleReaderIOException ex) {
                    ex.setSeResponse(new SeResponse(true, true, null, apduResponses));
                    throw ex;
                }
                return new SeResponse(true, true, null, apduResponses);
            }
        }).when(mockReader).transmitSeRequest(any(SeRequest.class), any(ChannelControl.class));
        return mockReader;
    }

    private ApduResponse getResponses(String name, Map<String, String> hexCommands,
            ApduRequest apduRequest) throws KeypleReaderIOException {
        String hexApdu = ByteArrayUtil.toHex(apduRequest.getBytes());
        // return matching hexa response if found
        if (hexCommands.containsKey(hexApdu)) {
            return new ApduResponse(ByteArrayUtil.fromHex(hexCommands.get(hexApdu)), null);
        }
        System.out.println(name + ": no response available for " + hexApdu);
        // throw a KeypleReaderIOException if not found
        throw new KeypleReaderIOException("No response available for this request.");
    }
}
