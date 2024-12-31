import {FastifyInstance} from "fastify";

export default (fastify: FastifyInstance) => {
    fastify.get('/', (_, reply) => {
        reply.redirect('https://beemo.gg')
    })
}