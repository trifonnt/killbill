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

import java.math.BigDecimal;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.util.currency.KillBillMoney;

public class InvoiceDateUtils {

    /**
     * Called internally to calculate proration or when we recalculate approximate repair amount
     *
     * @param startDate                start date of the prorated interval
     * @param endDate                  end date of the prorated interval
     * @param previousBillingCycleDate start date of the period
     * @param nextBillingCycleDate     end date of the period
     * @return
     */
    public static BigDecimal calculateProrationBetweenDates(final LocalDate startDate, final LocalDate endDate, final LocalDate previousBillingCycleDate, final LocalDate nextBillingCycleDate) {
        final int daysBetween = Days.daysBetween(previousBillingCycleDate, nextBillingCycleDate).getDays();
        return calculateProrationBetweenDates(startDate, endDate, daysBetween);
    }

    public static BigDecimal calculateProrationBetweenDates(final LocalDate startDate, final LocalDate endDate, int daysBetween) {
        if (daysBetween <= 0) {
            return BigDecimal.ZERO;
        }

        final BigDecimal daysInPeriod = new BigDecimal(daysBetween);
        final BigDecimal days = new BigDecimal(Days.daysBetween(startDate, endDate).getDays());

        return days.divide(daysInPeriod, KillBillMoney.MAX_SCALE, KillBillMoney.ROUNDING_METHOD);
    }

    public static BigDecimal calculateProRationBeforeFirstBillingPeriod(final LocalDate startDate, final LocalDate nextBillingCycleDate,
                                                                        final BillingPeriod billingPeriod) {
        final LocalDate previousBillingCycleDate = nextBillingCycleDate.plusMonths(-billingPeriod.getNumberOfMonths());

        return calculateProrationBetweenDates(startDate, nextBillingCycleDate, previousBillingCycleDate, nextBillingCycleDate);
    }

    public static int calculateNumberOfWholeBillingPeriods(final LocalDate startDate, final LocalDate endDate, final BillingPeriod billingPeriod) {
        final int numberOfMonths = Months.monthsBetween(startDate, endDate).getMonths();
        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        return numberOfMonths / numberOfMonthsInPeriod;
    }

    public static LocalDate calculateLastBillingCycleDateBefore(final LocalDate date, final LocalDate previousBillCycleDate,
                                                                final int billingCycleDay, final BillingPeriod billingPeriod) {
        LocalDate proposedDate = previousBillCycleDate;

        int numberOfPeriods = 0;
        while (!proposedDate.isAfter(date)) {
            proposedDate = previousBillCycleDate.plusMonths(numberOfPeriods * billingPeriod.getNumberOfMonths());
            numberOfPeriods += 1;
        }

        proposedDate = proposedDate.plusMonths(-billingPeriod.getNumberOfMonths());

        if (proposedDate.dayOfMonth().get() < billingCycleDay) {
            final int lastDayOfTheMonth = proposedDate.dayOfMonth().getMaximumValue();
            if (lastDayOfTheMonth < billingCycleDay) {
                proposedDate = new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), lastDayOfTheMonth);
            } else {
                proposedDate = new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), billingCycleDay);
            }
        }

        if (proposedDate.isBefore(previousBillCycleDate)) {
            // Make sure not to go too far in the past
            return previousBillCycleDate;
        } else {
            return proposedDate;
        }
    }

    public static LocalDate calculateEffectiveEndDate(final LocalDate billCycleDate, final LocalDate targetDate,
                                                      final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(billCycleDate)) {
            return billCycleDate;
        }

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        int numberOfPeriods = 0;
        LocalDate proposedDate = billCycleDate;

        while (!proposedDate.isAfter(targetDate)) {
            proposedDate = billCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
            numberOfPeriods += 1;
        }

        return proposedDate;
    }

    public static LocalDate calculateEffectiveEndDate(final LocalDate billCycleDate, final LocalDate targetDate,
                                                      final LocalDate endDate, final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(endDate)) {
            if (targetDate.isBefore(billCycleDate)) {
                return billCycleDate;
            }

            final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
            int numberOfPeriods = 0;
            LocalDate proposedDate = billCycleDate;

            while (!proposedDate.isAfter(targetDate)) {
                proposedDate = billCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
                numberOfPeriods += 1;
            }

            // the current period includes the target date
            // check to see whether the end date truncates the period
            if (endDate.isBefore(proposedDate)) {
                return endDate;
            } else {
                return proposedDate;
            }
        } else {
            return endDate;
        }
    }

    public static BigDecimal calculateProRationAfterLastBillingCycleDate(final LocalDate endDate, final LocalDate previousBillThroughDate,
                                                                         final BillingPeriod billingPeriod) {
        // Note: assumption is that previousBillThroughDate is correctly aligned with the billing cycle day
        final LocalDate nextBillThroughDate = previousBillThroughDate.plusMonths(billingPeriod.getNumberOfMonths());
        return calculateProrationBetweenDates(previousBillThroughDate, endDate, previousBillThroughDate, nextBillThroughDate);
    }

 /*
    public static LocalDate calculateBillingCycleDateOnOrAfter(final LocalDate date, final DateTimeZone accountTimeZone,
                                                               final int billingCycleDayLocal) {
        final DateTime tmp = date.toDateTimeAtStartOfDay(accountTimeZone);
        final DateTime proposedDateTime = calculateBillingCycleDateOnOrAfter(tmp, billingCycleDayLocal);

        return new LocalDate(proposedDateTime, accountTimeZone);
    }

    public static LocalDate calculateBillingCycleDateAfter(final LocalDate date, final DateTimeZone accountTimeZone,
                                                           final int billingCycleDayLocal) {
        final DateTime tmp = date.toDateTimeAtStartOfDay(accountTimeZone);
        final DateTime proposedDateTime = calculateBillingCycleDateAfter(tmp, billingCycleDayLocal);

        return new LocalDate(proposedDateTime, accountTimeZone);
    }
    */

    public static LocalDate calculateBillingCycleDateOnOrAfter(final LocalDate date, final int billingCycleDayLocal) {
        final int lastDayOfMonth = date.dayOfMonth().getMaximumValue();

        final LocalDate fixedDate;
        if (billingCycleDayLocal > lastDayOfMonth) {
            fixedDate = new LocalDate(date.getYear(), date.getMonthOfYear(), lastDayOfMonth, date.getChronology());
        } else {
            fixedDate = new LocalDate(date.getYear(), date.getMonthOfYear(), billingCycleDayLocal, date.getChronology());
        }

        LocalDate proposedDate = fixedDate;
        while (proposedDate.isBefore(date)) {
            proposedDate = proposedDate.plusMonths(1);
        }
        return proposedDate;
    }

    public static LocalDate calculateBillingCycleDateAfter(final LocalDate date, final int billingCycleDayLocal) {
        LocalDate proposedDate = calculateBillingCycleDateOnOrAfter(date, billingCycleDayLocal);
        if (date.compareTo(proposedDate) == 0) {
            proposedDate = proposedDate.plusMonths(1);
        }
        return proposedDate;
    }
}
