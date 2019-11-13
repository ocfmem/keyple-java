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
package org.eclipse.keyple.core.seproxy.plugin.local;

import static org.eclipse.keyple.core.seproxy.plugin.local.AbstractObservableState.MonitoringState.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.eclipse.keyple.core.CoreBaseTest;
import org.eclipse.keyple.core.seproxy.plugin.local.state.WaitForSeInsertion;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitForSeInsertionTest extends CoreBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(WaitForSeInsertionTest.class);

    final String PLUGIN_NAME = "WaitForSeInsertionTestP";
    final String READER_NAME = "WaitForSeInsertionTest";

    AbstractObservableState waitForInsert;
    AbstractObservableLocalReader r;

    @Before
    public void setUp() {
        logger.info("------------------------------");
        logger.info("Test {}", name.getMethodName() + "");
        logger.info("------------------------------");

        r = AbsSmartInsertionTheadedReaderTest.getMock(READER_NAME);
        waitForInsert = new WaitForSeInsertion(r);

    }

    @Before
    public void tearDown() {
        logger.info("******************************");
        logger.info("End of Test {}", name.getMethodName() + "");
        logger.info("\"******************************");

        waitForInsert.onDeactivate();

    }

    @Test
    public void insertSe_matched() throws Exception {
        /*
         * input SE inserted SE matched
         */
        doReturn(true).when(r).processSeInserted();

        /* test */
        waitForInsert.onActivate();
        waitForInsert.onEvent(AbstractObservableLocalReader.InternalEvent.SE_INSERTED);

        /* Assert */
        verify(r, times(1)).switchState(WAIT_FOR_SE_PROCESSING);

        // Assert.assertEquals(WAIT_FOR_SE_PROCESSING, r.getCurrentState().getMonitoringState());

    }

    @Test
    public void testInsertSe_Notmatched() throws Exception {
        /*
         * input SE inserted SE doesnt matched Back to Detection
         */
        doReturn(false).when(r).processSeInserted();

        /* test */
        waitForInsert.onActivate();
        waitForInsert.onEvent(AbstractObservableLocalReader.InternalEvent.SE_INSERTED);
        /* Assert */

        // stay in same state
        verify(r, times(0)).switchState(any(AbstractObservableState.MonitoringState.class));
    }

    // @Test
    // public void testTimeout() throws Exception {
    // /*
    // * input no SE inserted within timeout
    // */
    //
    // /* test */
    // waitForInsert.onActivate();
    // waitForInsert.onEvent(AbstractObservableLocalReader.InternalEvent.TIME_OUT);
    //
    // /* Assert */
    // verify(r, times(1)).switchState(WAIT_FOR_SE_INSERTION);
    // }

}
