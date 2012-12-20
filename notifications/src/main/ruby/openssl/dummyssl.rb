###################################################################################
#                                                                                 #
#                   Copyright 2010-2012 Ning, Inc.                                #
#                                                                                 #
#      Ning licenses this file to you under the Apache License, version 2.0       #
#      (the "License"); you may not use this file except in compliance with the   #
#      License.  You may obtain a copy of the License at:                         #
#                                                                                 #
#          http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                                 #
#      Unless required by applicable law or agreed to in writing, software        #
#      distributed under the License is distributed on an "AS IS" BASIS, WITHOUT  #
#      WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the  #
#      License for the specific language governing permissions and limitations    #
#      under the License.                                                         #
#                                                                                 #
###################################################################################

warn "Warning: OpenSSL SSL implementation unavailable"
warn "You must run on JDK 1.5 (Java 5) or higher to use SSL"
module OpenSSL
  module SSL
    class SSLError < OpenSSLError; end
    class SSLContext; end
    class SSLSocket; end
    VERIFY_NONE = 0
    VERIFY_PEER = 1
    VERIFY_FAIL_IF_NO_PEER_CERT = 2
    VERIFY_CLIENT_ONCE = 4
    OP_ALL = 0x00000FFF
  end
end
