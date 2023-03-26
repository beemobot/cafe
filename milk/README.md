##

Milk is the module for handling message archival, and raid logs on Beemo, it can serve as either or both a scalable writer to the database for raids 
and message archives, or an open, and scalable web server for the public to read logs from.

### 📦 Setup

> **Warning**
> 
> This assumes that the `.env` is configured properly and contains all the properties that are needed. If not, please ensure that is done first.
> 
> Additionally, make sure that this does not use the same database as Tea or any existing project, it can be on the same instance, but ensure that the database itself is different. Prisma will ask you to reset the database if that happens, that's when you know it's the same table.

> **Note**
> 
> You can enable both writes and reads at the same time, but for best scalability, please separate the two into two nodes (milk-writes and milk-reads) 
> since we don't want either node to interfere with each other especially on peak loads on either side. It is highly recommended to use Docker Swarm 
> or Kubernetes for this.

In order to set up Milk, you first have to build the Docker image by running the following:
```shell
docker build -t milk .
```

After creating the image, there are two ways that you can approach this, either run Docker Swarm or a single container. Each has their own uses, but 
for future scalability, it is recommended to use Docker Swarm which is much simpler to use than Kubernetes and can automatically load-balance.

To create a single container, you can simply run the following command:
```shell
docker run --name milk -p [server_port]:[server_port] --env-file .env milk
```

**RECOMMENDED** 

> **Warning**
> 
> When using Docker Swarm, replace all `127.0.0.1` or `localhost` to the host machine's address `172.17.0.1` otherwise 
> the container won't be able to access the service. You can also use another Docker network instead.

> **Warning**
> 
> Additional configuration may be needed to configure Kafka to advertise the `172.17.0.1` port, and you generally shouldn't 
> use the host address anyway when configuring for a Swarm since there can be more than one machines involved.

Before creating a Swarm of containers, you have to first configure the additional configurations in the Compose file, before we 
can continue though, first rename the file into `docker-compose.yml`:
```shell
$ mv docker-compose.example.yml docker-compose.yml
```

And configure what is needed, whether it'd be the amount of replicas per service or the server port. To modify the replica count 
for a service, simply change the `replicas` field to the number you like. You can also scale this up automatically if you have Portainer.

After configuration is done, you can run the following commands:
```shell
$ docker stack deploy --compose-file docker-compose.yml milk

Creating network milk_default
Creating service milk_reads
Creating service milk_writes

# Validate that the service does exist. (OPTIONAL STEP)
$ docker stack services milk

ID             NAME          MODE         REPLICAS   IMAGE         PORTS
xhqpanrufsw2   milk_reads    replicated   3/3        milk:latest   
z3uahn97e6w4   milk_writes   replicated   2/2        milk:latest   
```

Once you have set up the swarm, you can notice that the load is distributed amongst all the nodes and that is why swarm is recommended 
for massive amount of loads.

To update the swarm, you can run the following:
```shell
docker build -t milk .
docker stack deploy --compose-file docker-compose.yml milk
```

To delete the swarm you can run the following:
```shell
docker stack rm milk
```

To scale a specific service, you can run either of the following:
```shell
# Scaling the writes up to 5 nodes.
docker service scale milk_writes=5

# Scaling the reads up to 5 nodes.
docker service scale milk_reads=5
```

> **Note**
> 
> `[server_port]` should be the server port that you configured on the configuration.
