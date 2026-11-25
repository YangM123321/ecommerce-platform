# config-repo

Settings for every service, served by **config-server**. A service named `order-service`
running with profile `local` receives, in increasing priority:

1. `application.yml` - shared by every service
2. `application-local.yml` - shared, local environment only
3. `order-service.yml` - this service, every environment
4. `order-service-local.yml` - this service, local only (if it exists)

Check what a service gets: http://localhost:8888/order-service/local

**Never put real secrets here.** Values like `${JWT_SECRET}` are placeholders filled in
from environment variables on the machine running the service.
