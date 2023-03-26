import {FastifyInstance} from "fastify";

export type Attachable = {
    attach: (server: FastifyInstance) => any
}