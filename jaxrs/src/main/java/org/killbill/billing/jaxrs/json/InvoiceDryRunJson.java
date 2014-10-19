/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.json;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvoiceDryRunJson {

    private final String dryRunAction;
    private final String phaseType;
    private final String productName;
    private final String productCategory;
    private final String billingPeriod;
    private final String priceListName;
    private final LocalDate effectiveDate;
    private final String subscriptionId;

    @JsonCreator
    public InvoiceDryRunJson(@JsonProperty("dryRunAction") final String dryRunAction,
                             @JsonProperty("phaseType") final String phaseType,
                             @JsonProperty("productName") final String productName,
                             @JsonProperty("productCategory") final String productCategory,
                             @JsonProperty("billingPeriod") final String billingPeriod,
                             @JsonProperty("priceListName") final String priceListName,
                             @JsonProperty("subscriptionId") final String subscriptionId,
                             @JsonProperty("effectiveDate") final LocalDate effectiveDate) {
        this.dryRunAction = dryRunAction;
        this.phaseType = phaseType;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceListName = priceListName;
        this.subscriptionId = subscriptionId;
        this.effectiveDate = effectiveDate;
    }

    public String getDryRunAction() {
        return dryRunAction;
    }

    public String getPhaseType() {
        return phaseType;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceListName() {
        return priceListName;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
}
