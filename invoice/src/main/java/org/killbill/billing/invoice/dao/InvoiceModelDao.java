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

package org.killbill.billing.invoice.dao;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class InvoiceModelDao extends EntityModelDaoBase implements EntityModelDao<Invoice> {

    private UUID accountId;
    private Integer invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate targetDate;
    private Currency currency;
    private boolean migrated;

    // Not in the database, for convenience only
    private List<InvoiceItemModelDao> invoiceItems = new LinkedList<InvoiceItemModelDao>();
    private List<InvoicePaymentModelDao> invoicePayments = new LinkedList<InvoicePaymentModelDao>();
    private Currency processedCurrency;

    public InvoiceModelDao() { /* For the DAO mapper */ }

    public InvoiceModelDao(final UUID id, @Nullable final DateTime createdDate, final UUID accountId,
                           @Nullable final Integer invoiceNumber, final LocalDate invoiceDate, final LocalDate targetDate,
                           final Currency currency, final boolean migrated) {
        super(id, createdDate, createdDate);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.migrated = migrated;
    }

    public InvoiceModelDao(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency, final boolean migrated) {
        this(UUID.randomUUID(), null, accountId, null, invoiceDate, targetDate, currency, migrated);
    }

    public InvoiceModelDao(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency) {
        this(UUID.randomUUID(), null, accountId, null, invoiceDate, targetDate, currency, false);
    }

    public InvoiceModelDao(final Invoice invoice) {
        this(invoice.getId(), invoice.getCreatedDate(), invoice.getAccountId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
             invoice.getTargetDate(), invoice.getCurrency(), invoice.isMigrationInvoice());
    }

    public void addInvoiceItems(final List<InvoiceItemModelDao> invoiceItems) {
        this.invoiceItems.addAll(invoiceItems);
    }

    public void addInvoiceItem(final InvoiceItemModelDao invoiceItem) {
        this.invoiceItems.add(invoiceItem);
    }

    public List<InvoiceItemModelDao> getInvoiceItems() {
        return invoiceItems;
    }

    public void addPayments(final List<InvoicePaymentModelDao> invoicePayments) {
        this.invoicePayments.addAll(invoicePayments);
    }

    public List<InvoicePaymentModelDao> getInvoicePayments() {
        return invoicePayments;
    }

    public void setProcessedCurrency(Currency currency) {
        this.processedCurrency = currency;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency != null ? processedCurrency : currency;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public void setInvoiceNumber(final Integer invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public void setInvoiceDate(final LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public void setTargetDate(final LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public void setMigrated(final boolean migrated) {
        this.migrated = migrated;
    }

    public void setInvoiceItems(final List<InvoiceItemModelDao> invoiceItems) {
        this.invoiceItems = invoiceItems;
    }

    public void setInvoicePayments(final List<InvoicePaymentModelDao> invoicePayments) {
        this.invoicePayments = invoicePayments;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", currency=").append(currency);
        sb.append(", migrated=").append(migrated);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final InvoiceModelDao that = (InvoiceModelDao) o;

        if (migrated != that.migrated) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (targetDate != null ? !targetDate.equals(that.targetDate) : that.targetDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (migrated ? 1 : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICES;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

}
