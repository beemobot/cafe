import NodeCache from "node-cache";
import {FastifyReply} from "fastify";
import {TEN_MINUTES} from "../constants/time.js";

const cache = new NodeCache({ stdTTL: TEN_MINUTES })
export const useCacheWhenPossible = async (
    reply: FastifyReply,
    key: string,
    computation:  () => Promise<{ result: string, shouldCache: boolean }>
): Promise<FastifyReply> => {
    const cachedResult = cache.get<string>(key)
    if (cachedResult != null) {
        return reply
            .header('X-Cache', 'HIT')
            .header('X-Cache-Expires', cache.getTtl(key))
            .send(cachedResult)
    }

    const { result, shouldCache } = await computation()
    if (shouldCache) {
        cache.set(key, result)
    }
    return reply
        .status(200)
        .header('X-Cache', 'MISS')
        .send(result)
}