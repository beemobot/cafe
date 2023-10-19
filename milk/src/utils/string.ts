import {randomNumber} from "./number.js";

export const randomString = (length: number): string => {
    let contents = '';
    while (contents.length < length) {
        let seed = randomNumber(1, 4);
        switch (seed) {
            case 1: {
                seed = randomNumber(65, 91);
                contents += String.fromCharCode(seed);
                break;
            }
            case 2: {
                seed = randomNumber(97, 123);
                contents += String.fromCharCode(seed);
                break;
            }
            case 3: {
                seed = randomNumber(0, 10);
                contents += seed;
                break;
            }
        }
    }
    return contents
}