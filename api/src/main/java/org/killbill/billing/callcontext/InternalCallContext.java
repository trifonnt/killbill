/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.joda.time.DateTimeZone;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;

/**
 * Internal use only
 */
public class InternalCallContext extends InternalTenantContext {

    private final UUID userToken;
    private final String createdBy;
    private final String updatedBy;
    private final CallOrigin callOrigin;
    private final UserType contextUserType;
    private final String reasonCode;
    private final String comments;
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public InternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final UUID userToken, final String userName,
                               final CallOrigin callOrigin, final UserType userType, final String reasonCode, final String comment,
                               final DateTime createdDate, final DateTime updatedDate) {
        super(tenantRecordId, accountRecordId);
        this.userToken = userToken;
        this.createdBy = userName;
        this.updatedBy = userName;
        this.callOrigin = callOrigin;
        this.contextUserType = userType;
        this.reasonCode = reasonCode;
        this.comments = comment;
        this.createdDate = new DateTime(createdDate, DateTimeZone.UTC);
        this.updatedDate = updatedDate;
    }

    public InternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final CallContext callContext) {
        this(tenantRecordId, accountRecordId, callContext.getUserToken(), callContext.getUserName(), callContext.getCallOrigin(),
             callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getCreatedDate(),
             callContext.getUpdatedDate());
    }

    public InternalCallContext(final InternalCallContext context, final Long accountRecordId) {
        this(context.getTenantRecordId(), accountRecordId, context.getUserToken(), context.getCreatedBy(), context.getCallOrigin(),
             context.getContextUserType(), context.getReasonCode(), context.getComments(), context.getCreatedDate(),
             context.getUpdatedDate());
    }

    // TODO should not be needed if all services are using internal API
    // Unfortunately not true as some APIs ae hidden in object -- e.g OverdueStateApplicator is doing subscription.cancelEntitlementWithDateOverrideBillingPolicy
    public CallContext toCallContext(final UUID tenantId) {
        return new DefaultCallContext(tenantId, createdBy, callOrigin, contextUserType, reasonCode, comments, userToken, createdDate, updatedDate);
    }

    public UUID getUserToken() {
        return userToken;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    public UserType getContextUserType() {
        return contextUserType;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComments() {
        return comments;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InternalCallContext");
        sb.append("{userToken=").append(userToken);
        sb.append(", createdBy='").append(createdBy).append('\'');
        sb.append(", updatedBy='").append(updatedBy).append('\'');
        sb.append(", callOrigin=").append(callOrigin);
        sb.append(", contextUserType=").append(contextUserType);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comments='").append(comments).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
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

        final InternalCallContext that = (InternalCallContext) o;

        if (callOrigin != that.callOrigin) {
            return false;
        }
        if (comments != null ? !comments.equals(that.comments) : that.comments != null) {
            return false;
        }
        if (createdBy != null ? !createdBy.equals(that.createdBy) : that.createdBy != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (reasonCode != null ? !reasonCode.equals(that.reasonCode) : that.reasonCode != null) {
            return false;
        }
        if (updatedBy != null ? !updatedBy.equals(that.updatedBy) : that.updatedBy != null) {
            return false;
        }
        if (updatedDate != null ? !updatedDate.equals(that.updatedDate) : that.updatedDate != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }
        if (contextUserType != that.contextUserType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (createdBy != null ? createdBy.hashCode() : 0);
        result = 31 * result + (updatedBy != null ? updatedBy.hashCode() : 0);
        result = 31 * result + (callOrigin != null ? callOrigin.hashCode() : 0);
        result = 31 * result + (contextUserType != null ? contextUserType.hashCode() : 0);
        result = 31 * result + (reasonCode != null ? reasonCode.hashCode() : 0);
        result = 31 * result + (comments != null ? comments.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        return result;
    }
}
