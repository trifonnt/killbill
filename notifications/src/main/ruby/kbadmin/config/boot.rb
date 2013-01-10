# TODO For some reason, Jquery::Rails::Railtie::PROTOTYPE_JS is not defined?
module Rails
  class Railtie
  end
end
module Jquery
  module Rails
    class Railtie < ::Rails::Railtie
    end
  end
end
Jquery::Rails::Railtie::PROTOTYPE_JS = ["prototype", "effects", "dragdrop", "controls"]

# TODO Fix Bundler leakage (use only bundler and gems in the jar and not on the filesystem)
# See related issue https://github.com/jruby/jruby/issues/401
ENV["GEM_HOME"] = "classpath:/gemrepo"
ENV["GEM_PATH"] = ENV["GEM_HOME"]

require 'rubygems'

# TODO The original Gem::Specification code looks for gemspec files by using a Glob, which
# doesn't work for files on the classpath. Instead, we rely on the magic gemrepo.gemspec
# file we generated (see bundler-packaging-plugin) to tell us where to load gemspecs from
module Gem
  class Specification
    def self._all # :nodoc:
      unless defined?(@@all) && @@all then
        specs = {}

        File.read("classpath:/gemrepo/META-INF/gemrepo.gemspec").split("\n").each do |gemspec|
          gemspec_path = "classpath:/gemrepo/specifications/#{gemspec}"
          code = File.read gemspec_path

          begin
            spec = eval code, binding, gemspec_path

            if Gem::Specification === spec
              spec.loaded_from = gemspec_path
              specs[spec.full_name] ||= spec
            else
              warn "[#{gemspec_path}] isn't a Gem::Specification (#{spec.class} instead)."
            end
          rescue SignalException, SystemExit
            raise
          rescue SyntaxError, Exception => e
            warn "Invalid gemspec in [#{gemspec_path}]: #{e}"
          end
        end

        @@all = specs.values

        _resort!
      end
      @@all
    end
  end
end

# Set up gems listed in the Gemfile.
# Note! This differs from a vanilla Rails app as we point to the top-level Gemfile
# File.expand_path will do the right thing and expand it to classpath:/Gemfile as needed
ENV['BUNDLE_GEMFILE'] ||= File.expand_path('../../../../../Gemfile', __FILE__)

# TODO Required to boostrap Bundler
gem 'bundler'

require 'bundler/shared_helpers'
require 'bundler'

# TODO Remove the Bundler check: gemfile.file? which doesn't work on the classpath
module Bundler
  class Definition
    def self.build(gemfile, lockfile, unlock)
      unlock ||= {}
      gemfile = Pathname.new(gemfile).expand_path
      Dsl.evaluate(gemfile, lockfile, unlock)
    end
  end
end

require 'bundler/setup' if File.exists?(ENV['BUNDLE_GEMFILE'])
