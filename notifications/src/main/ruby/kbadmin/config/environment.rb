# Load the rails application
require File.expand_path('../application', __FILE__)

# Initialize the rails application
Kbadmin::Application.initialize!

# TODO Need to load the environment manually
require File.expand_path("../environments/#{ENV['RAILS_ENV'] || 'development'}", __FILE__)
require File.expand_path("../initializers/kaui.rb", __FILE__)
require File.expand_path("../initializers/secret_token.rb", __FILE__)
require File.expand_path("../initializers/session_store.rb", __FILE__)
require File.expand_path("../initializers/wrap_parameters.rb", __FILE__)
