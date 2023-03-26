import {NumberUtil} from "./number.js";

export const random = (length: number): string => {
    let contents = '';
    while (contents.length < length) {
        let seed = NumberUtil.random(1, 4);
        switch (seed) {
            case 1: {
                seed = NumberUtil.random(65, 91);
                contents += String.fromCharCode(seed);
                break;
            }
            case 2: {
                seed = NumberUtil.random(97, 123);
                contents += String.fromCharCode(seed);
                break;
            }
            case 3: {
                seed = NumberUtil.random(0, 10);
                contents += seed;
                break;
            }
        }
    }
    return contents
}

export const StringUtil = { random: random }