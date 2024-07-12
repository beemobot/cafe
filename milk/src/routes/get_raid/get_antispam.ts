import {FastifyReply, FastifyRequest} from "fastify";
import {RaidParameter} from "../get_raid.js";

export async function route$GetAntispam(request: FastifyRequest<RaidParameter>, reply: FastifyReply): Promise<FastifyReply> {
    let { id } = request.params
    return reply.redirect('/raid/' + encodeURIComponent(id))
}