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

package org.killbill.billing.invoice.tests;

import static org.killbill.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.model.BillingModeGenerator;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.model.RecurringInvoiceItemData;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public abstract class ProRationTestBase extends InvoiceTestSuiteNoDB {

    protected abstract BillingModeGenerator getBillingMode();

    protected abstract BillingPeriod getBillingPeriod();

    protected void testCalculateNumberOfBillingCycles(final LocalDate startDate, final LocalDate targetDate, final int billingCycleDay, final BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            final BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay);

            assertEquals(numberOfBillingCycles.compareTo(expectedValue), 0, "Actual: " + numberOfBillingCycles.toString() + "; expected: " + expectedValue.toString());
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    protected void testCalculateNumberOfBillingCycles(final LocalDate startDate, final LocalDate endDate, final LocalDate targetDate, final int billingCycleDay, final BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            final BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay);

            assertEquals(numberOfBillingCycles.compareTo(expectedValue), 0, "Actual: " + numberOfBillingCycles.toString() + "; expected: " + expectedValue.toString());
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    protected BigDecimal calculateNumberOfBillingCycles(final LocalDate startDate, final LocalDate endDate, final LocalDate targetDate, final int billingCycleDay) throws InvalidDateSequenceException {
        final List<RecurringInvoiceItemData> items = getBillingMode().generateInvoiceItemData(startDate, endDate, targetDate, billingCycleDay, getBillingPeriod());

        BigDecimal numberOfBillingCycles = ZERO;
        for (final RecurringInvoiceItemData item : items) {
            numberOfBillingCycles = numberOfBillingCycles.add(item.getNumberOfCycles());
        }

        return numberOfBillingCycles;
    }

    protected BigDecimal calculateNumberOfBillingCycles(final LocalDate startDate, final LocalDate targetDate, final int billingCycleDay) throws InvalidDateSequenceException {
        final List<RecurringInvoiceItemData> items = getBillingMode().generateInvoiceItemData(startDate, null, targetDate, billingCycleDay, getBillingPeriod());

        BigDecimal numberOfBillingCycles = ZERO;
        for (final RecurringInvoiceItemData item : items) {
            numberOfBillingCycles = numberOfBillingCycles.add(item.getNumberOfCycles());
        }

        return numberOfBillingCycles;
    }
}
