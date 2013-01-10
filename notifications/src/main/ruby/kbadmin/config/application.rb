require File.expand_path('../boot', __FILE__)

# Pick the frameworks you want:
# require "active_record/railtie"
require "action_controller/railtie"
require "action_mailer/railtie"
require "active_resource/railtie"
require "sprockets/railtie"
require "rails/test_unit/railtie"

# TODO Workaround around Dir.chdir not working in jars (needs to be early in the initialization process)
# See related issue: https://github.com/carlhuda/bundler/issues/1865
module Rails
  module Paths
    class Path < Array
      private
      def expand_dir(path, glob)
        # TODO Should we check if path  =~ /^jar:file/?
        Dir.chdir(path) do
          Dir.glob(@glob).map { |file| File.join path, file }.sort
        end rescue Dir.glob(@glob).map { |file| File.join path, file }.sort
      end
    end
  end
end

require 'active_support/dependencies'
module ActiveSupport #:nodoc:
  module Dependencies #:nodoc:
    module Loadable #:nodoc:
      def load_dependency(file)
        if Dependencies.load? && ActiveSupport::Dependencies.constant_watch_stack.watching?
          Dependencies.new_constants_in(Object) { yield }
        else
          yield
        end
      rescue Exception => exception  # errors from loading file
        # TODO We don't raise (related to the hack above)
        # Note that we can't ignore all errors here!
        if file =~ /^jar:file/
          puts "Unable to load #{file} (ignored): #{exception}"
        else
          exception.blame_file! file
          raise
        end
      end
    end
  end
end

# TODO Workaround JRuby bug
# See:
# https://jira.codehaus.org/browse/JRUBY-3986
# https://github.com/jruby/jruby/pull/142
# https://github.com/jruby/warbler/issues/79
module ActiveSupport
  class FileUpdateChecker
    private
    def updated_at #:nodoc:
      @updated_at || begin
        all = []
        all.concat @files.select { |f| File.exists?(f) }
        all.concat Dir[@glob] if @glob
        all.map { |path| File.mtime(path) }.max || Time.at(0) rescue Time.at(0)
      end
    end
  end
end

require 'rails/engine'
module Rails
  class Engine < Railtie
    class << self
      protected

      def find_root_with_flag(flag, default=nil)
        root_path = self.class.called_from

        while root_path && File.directory?(root_path) && !File.exist?("#{root_path}/#{flag}")
          parent = File.dirname(root_path)
          root_path = parent != root_path && parent
        end

        # TODO Work around railties-3.2.9 path discovery issue in jars
        #root = File.exist?("#{root_path}/#{flag}") ? root_path : default
        #raise "Could not find root path for #{self}" unless root

        #RbConfig::CONFIG['host_os'] =~ /mswin|mingw/ ?
        #  Pathname.new(root).expand_path : Pathname.new(root).realpath
        Pathname.new(root_path)
      end
    end
  end
end

if defined?(Bundler)
  # TODO FIXME RuntimeError: Could not find root path for #<Coffee::Rails::Engine:0x9a8d9b>
  # find_root_with_flag at classpath:/gemrepo/gems/railties-3.2.9/lib/rails/engine.rb:635
  # The above doesn't work

  # If you precompile assets before deploying to production, use this line
  #Bundler.require(*Rails.groups(:assets => %w(development test)))
  # If you want your assets lazily compiled in production, use this line
  # Bundler.require(:default, :assets, Rails.env)
end

module Kbadmin
  class Application < Rails::Application
    # Settings in config/environments/* take precedence over those specified here.
    # Application configuration should go into files in config/initializers
    # -- all .rb files in that directory are automatically loaded.

    # Custom directories with classes and modules you want to be autoloadable.
    # config.autoload_paths += %W(#{config.root}/extras)

    # Only load the plugins named here, in the order given (default is alphabetical).
    # :all can be used as a placeholder for all plugins not explicitly named.
    # config.plugins = [ :exception_notification, :ssl_requirement, :all ]

    # Activate observers that should always be running.
    # config.active_record.observers = :cacher, :garbage_collector, :forum_observer

    # Set Time.zone default to the specified zone and make Active Record auto-convert to this zone.
    # Run "rake -D time" for a list of tasks for finding time zone names. Default is UTC.
    # config.time_zone = 'Central Time (US & Canada)'

    # The default locale is :en and all translations from config/locales/*.rb,yml are auto loaded.
    # config.i18n.load_path += Dir[Rails.root.join('my', 'locales', '*.{rb,yml}').to_s]
    # config.i18n.default_locale = :de

    # Configure the default encoding used in templates for Ruby 1.9.
    config.encoding = "utf-8"

    # Configure sensitive parameters which will be filtered from the log file.
    config.filter_parameters += [:password]

    # Enable escaping HTML in JSON.
    config.active_support.escape_html_entities_in_json = true

    # Use SQL instead of Active Record's schema dumper when creating the database.
    # This is necessary if your schema can't be completely dumped by the schema dumper,
    # like if you have constraints or database-specific column types
    # config.active_record.schema_format = :sql

    # Enforce whitelist mode for mass assignment.
    # This will create an empty whitelist of attributes available for mass-assignment for all models
    # in your app. As such, your models will need to explicitly whitelist or blacklist accessible
    # parameters by using an attr_accessible or attr_protected declaration.
    # config.active_record.whitelist_attributes = true

    # Enable the asset pipeline
    config.assets.enabled = true

    # Version of your assets, change this if you want to expire all your assets
    config.assets.version = '1.0'
  end
end

      # TODO Need to hardcode paths here since globing won't work on the classpath
      # Should be able to override Rails::Engine::Configuration < ::Rails::Railtie::Configuration
      # Note that most keys are hardcoded in the Rails source code
      # (see e.g. railties-3.2.9/lib/rails/engine/configuration.rb), so we can only
      # add new paths, not remove useless ones
