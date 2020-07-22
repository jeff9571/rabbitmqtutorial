
Publisher Confirms
	发布者确认是RabbitMQ的拓展，为了实现可信赖的发布。当通道开启“发布者确认”，客户端消息发布会被broker异步确认，意味着可以专注服务器端。

	概述
		这章我们将使用“发布者确认”确认发布的消息已经被broker安全地接收到，我们会涵盖集中策略去使用“发布者确认”并说明好处和弊端。

	通道开启“发布者确认”使能
		这是在AMQP0.9.1协议中发布，所有默认是关闭的，使用confirmSelect方法在通道级别开启。
			Channel channel = connection.createChannel();
			channel.confirmSelect();
			当你要打开“发布者确认”，必须在通道调用这个方法。应该开启一次，而不是每条消息都开启。

	策略1：	单独发布消息	
		从最简单开始发布确认，发布一条消息，同步等待它的确认。
		while (thereAreMessagesToPublish()) {
		    byte[] body = ...;
		    BasicProperties properties = ...;
		    channel.basicPublish(exchange, queue, properties, body);
		    // uses a 5 second timeout
		    channel.waitForConfirmsOrDie(5_000);
		}

		上一个例子我们发布消息并通过方法channel#waitForConfirmsOrDie等待消息确认。这个方法在消息被确认后返回。如果消息在预设时间中没有被确认，或则被否认（意味着broker因某种原因没有拿到消息），就会抛出异常，异常处理通常包括日志记录错误、尝试重新发送。
		不同的客户端库（jar）有不同的方式去同步处理“发布者确认”，所有要确保认真阅读自己当前使用的库文档说明。
		这技术很直接明了但也有确定：会严重拖慢消息发布的速度，但消息确认阻塞了会阻碍后面的消息发布。这种方法的吞吐量不会超过每秒发布的几百条消息。不过，对于某些应用程序来说，这已经足够好了。
			发布者确认是异步的吗？
			是，上面例子之所以同步是因为在外面套多了一层，实际上是异步的。

	策略2：批量发送消息
		优化上面的代码例子，我们可以批量发布消息，等所有消息都确认后在返回，下面举例批量100
		int batchSize = 100;
		int outstandingMessageCount = 0;
		while (thereAreMessagesToPublish()) {
		    byte[] body = ...;
		    BasicProperties properties = ...;
		    channel.basicPublish(exchange, queue, properties, body);
		    outstandingMessageCount++;
		    if (outstandingMessageCount == batchSize) {
		        ch.waitForConfirmsOrDie(5_000);
		        outstandingMessageCount = 0;
		    }
		}
		if (outstandingMessageCount > 0) {
		    ch.waitForConfirmsOrDie(5_000);
		}
		批量确认比起逐条确认，彻底提升吞吐量（每个RabbitMQ节点提升20-30倍），缺点就是我们报错的时候并不知道到底是在哪里出问题， 所以我们可能把内存所有的消息记录下来或者重新发布，但这种解决方法还是同步的，会阻塞发布消息。

	策略3：异步处理发布者确认	
		broker异步确认发布的消息只需要注册一个回调在客户端，就会收到消息确认的通知。
		Channel channel = connection.createChannel();
		channel.confirmSelect();
		channel.addConfirmListener((sequenceNumber, multiple) -> {
		    // code when message is confirmed
		}, (sequenceNumber, multiple) -> {
		    // code when message is nack-ed
		});
		这有两个回调一个是处理收到消息确认，一个是处理消息没被确认的
		sequenceNumber:标识已确认或已确认的消息的数字。我们将很快看到如何将其与已发布的消息关联起来。
		multiple：	这是一个布尔值。如果为假，则只确认一条消息，如果为真，则确认所有序列号较低或相等的消息。
		sequenceNumber可以通过以下方法在发布前获取。
		int sequenceNumber = channel.getNextPublishSeqNo());
		ch.basicPublish(exchange, queue, properties, body);




