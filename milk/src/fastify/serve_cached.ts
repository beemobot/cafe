import NodeCache from "node-cache";
import {FastifyReply} from "fastify";
import {TEN_MINUTES} from "../constants/time.js";

const cache = new NodeCache({ stdTTL: TEN_MINUTES })
export type CacheResult = { result: string | null, shouldCache: boolean }

const discordCacheResult = { result: null, shouldCache: false }
export const useCacheWhenPossible = async (
    reply: FastifyReply,
    key: string,
    contentType: string,
    computation:  (discard: CacheResult) => Promise<CacheResult>
): Promise<FastifyReply> => {
    const cachedResult = cache.get<string>(key)
    if (cachedResult != null) {
        return reply
            .header('X-Cache', 'HIT')
            .header('X-Cache-Expires', cache.getTtl(key))
            .header('Content-Type', contentType)
            .send(cachedResult)
    }

    const { result, shouldCache } = await computation(discordCacheResult)
    if (shouldCache) {
        cache.set(key, result)
    }
    return reply
        .status(200)
        .header('X-Cache', 'MISS')
        .header('Content-Type', contentType)
        .send(result)
}