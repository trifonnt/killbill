###################################################################################
#                                                                                 #
#                   Copyright 2010-2012 Ning, Inc.                                #
#                                                                                 #
#      Ning licenses this file to you under the Apache License, version 2.0       #
#      (the License); you may not use this file except in compliance with the   #
#      License.  You may obtain a copy of the License at:                         #
#                                                                                 #
#          http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                                 #
#      Unless required by applicable law or agreed to in writing, software        #
#      distributed under the License is distributed on an AS IS BASIS, WITHOUT  #
#      WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the  #
#      License for the specific language governing permissions and limitations    #
#      under the License.                                                         #
#                                                                                 #
###################################################################################

# Set APP_PATH to classpath:/kbadmin/config/application
APP_PATH = File.expand_path('../kbadmin/config/application',  __FILE__)

require File.expand_path('../kbadmin/config/boot',  __FILE__)
require 'rails/commands/server'

module Rails
  class Server < ::Rack::Server
      def default_options
        # TODO Hack to specify the path to our config.ru
        super.merge({
          :Port        => 3000,
          :environment => (ENV['RAILS_ENV'] || "development").dup,
          :daemonize   => false,
          :debugger    => false,
          :pid         => File.expand_path("tmp/server.pid"),
          :config      => 'classpath:/kbadmin/config.ru'
        })
      end
  end
end

Rails::Server.new.tap { |server|
    # We need to require application after the server sets environment,
    # otherwise the --environment option given to the server won't propagate.
    require APP_PATH
    Dir.chdir(Rails.application.root)
    server.start
}
