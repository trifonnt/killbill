/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.invoice.generator;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;

public class TestBillingIntervalDetail extends InvoiceTestSuiteNoDB {

    /*
     *
     *         Start         BCD    END_MONTH
     * |---------|------------|-------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate1() throws Exception {
        final LocalDate from = new LocalDate("2012-01-16");
        final int bcd = 17;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), bcd, BillingPeriod.ANNUAL);
        billingIntervalDetail.calculateFirstBillingCycleDate();
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-17"));
    }

    /*
     *
     *         Start             END_MONTH    BCD
     * |---------|-------------------| - - - -|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate2() throws Exception {
        final LocalDate from = new LocalDate("2012-02-16");
        final int bcd = 30;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), bcd, BillingPeriod.ANNUAL);
        billingIntervalDetail.calculateFirstBillingCycleDate();
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-29"));
    }

    /*
     * Here the interesting part is that BCD is prior start and
     *  i) we use MONTHLY billing period
     * ii) on the next month, there is no such date (2012-02-30 does not exist)
     *
     *                                      Start
     *                              BCD     END_MONTH
     * |----------------------------|--------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate4() throws Exception {
        final LocalDate from = new LocalDate("2012-01-31");
        final int bcd = 30;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), bcd, BillingPeriod.MONTHLY);
        billingIntervalDetail.calculateFirstBillingCycleDate();
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-29"));
    }

    /*
     *
     *         BCD                 Start      END_MONTH
     * |---------|-------------------|-----------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate3() throws Exception {
        final LocalDate from = new LocalDate("2012-02-16");
        final int bcd = 14;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), bcd, BillingPeriod.ANNUAL);
        billingIntervalDetail.calculateFirstBillingCycleDate();
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2013-02-14"));
    }

    @Test(groups = "fast")
    public void testNextBCDShouldNotBeInThePast() throws Exception {
        final LocalDate from = new LocalDate("2012-07-16");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 15, BillingPeriod.MONTHLY);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-08-15"));
    }

    @Test(groups = "fast")
    public void testBeforeBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-02");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-03");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-04");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-04-03"));
    }

    @Test(groups = "fast")
    public void testEffectiveEndDate() throws Exception {
        final LocalDate firstBCD = new LocalDate(2012, 7, 16);
        final LocalDate targetDate = new LocalDate(2012, 8, 16);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(firstBCD, null, targetDate, 16, billingPeriod);
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();
        Assert.assertEquals(effectiveEndDate, new LocalDate(2012, 9, 16));
    }

    @Test(groups = "fast")
    public void testLastBCD() throws Exception {
        final LocalDate start = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 9, 15); // so we get effectiveEndDate on 9/15
        final LocalDate targetDate = new LocalDate(2012, 8, 16);

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, endDate, targetDate, 16, BillingPeriod.MONTHLY);
        final LocalDate lastBCD = billingIntervalDetail.getLastBillingCycleDate();
        Assert.assertEquals(lastBCD, new LocalDate(2012, 8, 16));
    }

    @Test(groups = "fast")
    public void testLastBCDShouldNotBeBeforePreviousBCD() throws Exception {
        final LocalDate start = new LocalDate("2012-07-16");
        final int bcdLocal = 15;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, start, bcdLocal, BillingPeriod.MONTHLY);
        final LocalDate lastBCD = billingIntervalDetail.getLastBillingCycleDate();
        Assert.assertEquals(lastBCD, new LocalDate("2012-08-15"));
    }

    @Test(groups = "fast")
    public void testBCD31StartingWith30DayMonth() throws Exception {
        final LocalDate start = new LocalDate("2012-04-30");
        final LocalDate targetDate = new LocalDate("2012-04-30");
        final LocalDate end = null;
        final int bcdLocal = 31;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcdLocal, BillingPeriod.MONTHLY);
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();
        Assert.assertEquals(effectiveEndDate, new LocalDate("2012-05-31"));
    }

}
