import NodeCache from "node-cache";

export const AntispamLogsCache = new NodeCache({
    stdTTL: 10 * 1000 * 60
})