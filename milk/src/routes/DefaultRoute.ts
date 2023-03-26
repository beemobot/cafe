import {FastifyInstance} from "fastify";

const attach = (server: FastifyInstance) => server.get('/', (request, reply) => reply.redirect('https://beemo.gg'))
export const DefaultRoute = { attach: attach }