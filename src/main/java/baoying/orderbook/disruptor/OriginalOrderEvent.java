package baoying.orderbook.disruptor;

import baoying.orderbook.TradeMessage;

public class OriginalOrderEvent
{
    private TradeMessage.OriginalOrder _value;

    public void set(TradeMessage.OriginalOrder value)
    {
        this._value = value;
    }
}
