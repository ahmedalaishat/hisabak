SMS parse-status chip — four unmistakable states for the SMS inbox.

```jsx
<StatusChip status="linked" />     {/* green — imported, confirmed or rule-matched */}
<StatusChip status="unreviewed" /> {/* amber — imported, nobody checked it */}
<StatusChip status="parsed" />     {/* blue — ready to import */}
<StatusChip status="unparsed" />   {/* gray — no data */}
```