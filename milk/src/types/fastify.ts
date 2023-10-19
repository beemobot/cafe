import {FastifyInstance} from "fastify";

export type Attachable = (fastify: FastifyInstance) => void