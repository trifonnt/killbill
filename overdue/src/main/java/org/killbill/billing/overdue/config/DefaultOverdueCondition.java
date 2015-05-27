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

package org.killbill.billing.overdue.config;

import java.math.BigDecimal;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.ConditionEvaluation;
import org.killbill.billing.overdue.api.OverdueCondition;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.payment.api.PaymentResponse;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;

@XmlAccessorType(XmlAccessType.NONE)

public class DefaultOverdueCondition extends ValidatingConfig<DefaultOverdueConfig> implements ConditionEvaluation, OverdueCondition {

    @XmlElement(required = false, name = "numberOfUnpaidInvoicesEqualsOrExceeds")
    private Integer numberOfUnpaidInvoicesEqualsOrExceeds;

    @XmlElement(required = false, name = "totalUnpaidInvoiceBalanceEqualsOrExceeds")
    private BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds;

    @XmlElement(required = false, name = "timeSinceEarliestUnpaidInvoiceEqualsOrExceeds")
    private DefaultDuration timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;

    @XmlElementWrapper(required = false, name = "responseForLastFailedPaymentIn")
    @XmlElement(required = false, name = "response")
    private PaymentResponse[] responseForLastFailedPayment;

    @XmlElement(required = false, name = "controlTag")
    private ControlTagType controlTag;

    @Override
    public boolean evaluate(final BillingState state, final LocalDate date) {
        LocalDate unpaidInvoiceTriggerDate = null;
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null && state.getDateOfEarliestUnpaidInvoice() != null) {  // no date => no unpaid invoices
            unpaidInvoiceTriggerDate = state.getDateOfEarliestUnpaidInvoice().plus(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.toJodaPeriod());
        }

        return
                (numberOfUnpaidInvoicesEqualsOrExceeds == null || state.getNumberOfUnpaidInvoices() >= numberOfUnpaidInvoicesEqualsOrExceeds) &&
                (totalUnpaidInvoiceBalanceEqualsOrExceeds == null || totalUnpaidInvoiceBalanceEqualsOrExceeds.compareTo(state.getBalanceOfUnpaidInvoices()) <= 0) &&
                (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds == null ||
                 (unpaidInvoiceTriggerDate != null && !unpaidInvoiceTriggerDate.isAfter(date))) &&
                (responseForLastFailedPayment == null || responseIsIn(state.getResponseForLastFailedPayment(), responseForLastFailedPayment)) &&
                (controlTag == null || isTagIn(controlTag, state.getTags()));
    }

    private boolean responseIsIn(final PaymentResponse actualResponse,
                                 final PaymentResponse[] responseForLastFailedPayment) {
        for (final PaymentResponse response : responseForLastFailedPayment) {
            if (response.equals(actualResponse)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTagIn(final ControlTagType tagType, final Tag[] tags) {
        for (final Tag t : tags) {
            if (t.getTagDefinitionId().equals(tagType.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ValidationErrors validate(final DefaultOverdueConfig root,
                                     final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final DefaultOverdueConfig root, final URI uri) {
    }

    public Duration getTimeOffset() {
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null) {
            return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
        } else {
            return new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(0); // zero time
        }

    }

    @Override
    public Integer getNumberOfUnpaidInvoicesEqualsOrExceeds() {
        return numberOfUnpaidInvoicesEqualsOrExceeds;
    }

    @Override
    public BigDecimal getTotalUnpaidInvoiceBalanceEqualsOrExceeds() {
        return totalUnpaidInvoiceBalanceEqualsOrExceeds;
    }

    @Override
    public Duration getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() {
        return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
    }

    @Override
    public PaymentResponse [] getResponseForLastFailedPaymentIn() {
        return responseForLastFailedPayment;
    }

    @Override
    public ControlTagType getControlTagType() {
        return controlTag;
    }
}
