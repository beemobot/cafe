import {BrokerClient, IBrokerConnection} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {PREMIUM_PLAN_TOPIC} from "../../constants/kafka.js";
import {PremiumManagementData} from "../../types/kafka.js";

export class PremiumManagentClient extends BrokerClient<PremiumManagementData> {
    public constructor(connection: IBrokerConnection) {
        super(connection, PREMIUM_PLAN_TOPIC)
    }
}