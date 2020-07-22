
		假如我们要运行远程电脑上的一个功能，并且等待返回结果，这种模式就是RPC调用。
		本章将用RabbitMQ建立一个RP系统：一个客户端和一个可扩展的RPC服务器。我们将建一个简单的RPC服务（返回斐波那契数字）。
	
	客户端接口
		创建一个简单的client类，将暴露一个call方法，这个方法会发送一个RPC请求并一直阻塞直到结果返回。
		FibonacciRpcClient fibonacciRpc = new FibonacciRpcClient();
		String result = fibonacciRpc.call("4");
		System.out.println( "fib(4) is " + result);
		注意：
			虽然RPC很常见，但经常被讨论，当程序员忽视了方法是本地调用还是RPC调用时就会发生问题，结果就是不可预测的系统和给调试带来不必要的复杂性。滥用RPC会导致难以维护的垃圾代码。
			建议：1、确认是本地调用还是远程调用；2、文档记录系统，理清组件之间的依赖；3、处理错误，当RPC服务器长时间关闭时，客户机应该如何反应。
			如果条件允许，应该使用异步而不是RPC这种阻塞式。
			
	回调队列
		在RabbitMQ调用RPC很简单。客户端发消息，服务器端回复消息，为了接收响应，请求时需要发送一个回调队列地址，可以使用默认队列（排他的）
		callbackQueueName = channel.queueDeclare().getQueue();
		BasicProperties props = new BasicProperties
									.Builder()
									.replyTo(callbackQueueName)
									.build();
		channel.basicPublish("", "rpc_queue", props, message.getBytes());
		// ... then code to read a response message from the callback_queue ...
		
		消息属性：
			AMQP 0-9-1 协议预定义了14个消息的属性，大部分之前的例子已经用到，除了以下：
			deliverMode:2 表示消息持久化，其他数值则是暂存
			contentType:用于指定mime-type 的编码，如json就是application/json
			replyTo:命名回调队列
			correlationId:将RPC响应与请求关联起来非常有用
			
		新的导入：	import com.rabbitmq.client.AMQP.BasicProperties;
	correlation Id
		为每个rpc请求建立一个回调队列，效率低，有个更好的方式，为每个客户端建立一个回调队列。
		这有个新的问题，收到的响应不知道是对应哪一个请求，这时候就要用correlation id，把它设置为一个唯一的值对应一个请求，之后，当回调队列收到消息会依靠这个属性，这样就可以匹配响应和请求了。
		假如看到未知的correlation id，可能会丢弃安全地消息--它不属于我们的请求。
		你可能会问，为什么应该忽视未知的消息而不是返回错误？这是因为服务器端竞争条件的可能性，虽然可能性不高，但有可能RPC服务器在发送完消息给我们后死掉，但我们还没响应一个确认的消息给这请求。如果真的这样，重启的RPC服务器会再执行请求，这就是为什么在客户端我们必须处理重复的回应，而且RPC应该是幂等的。
		
		
	总结：
		1、对于RPC请求，客户端发送消息带两个属性：replyTo（为请求设置匿名排他队列），correlationId，为每个请求设置一个唯一的值。
		2、请求会发送到rpc_queue队列
		3、Rpc worker（也叫福服务器）在队列等待请求，当有请求时就开始工作并发送响应信息给客户端，从replyTo中使用队列
		4、客户端等待回复队列发来数据，当有数据时，会检查correlationId属性，如果匹配请求中的值，会返回响应到应用。
		
	组合起来
		斐波那契任务：
			private static int fib(int n) {
				if (n == 0) return 0;
				if (n == 1) return 1;
				return fib(n-1) + fib(n-2);
			}
		我们声明斐波那契函数。它假设输入只有有效的正整数。(不要期望这个方法适用于大的数字，它可能是最慢的递归实现)
		
		server
		先建立connection、channel，声明队列
		我们可能想运行不止一个server进程，为了多个server负载均衡，需要在channel.basicQos设置prefetchCount
		使用basicConsume去操作队列，它提供一个对象格式（DeliverCallback）的回调，它能执行具体操作并返回响应信息。
		
		client
		建立connection和channel
		首先生成一个唯一的correlationId数字并保存--消费者回调函数会使用这个值去配对对应的响应。
		然后创建一个专用、排他的队列，为了回复和订阅。
		接着，发布一个请求队列，带两个参数replyTo和correlationId。
		等待响应
		当消费者处理消息传递的逻辑封装在独立线程时，需要在收到回复前挂着main方法，为此可以使用BlockingQueue，代码中创建ArrayBlockingQueue并设置容量为1表示等待1个回复。
		消费者的工作内容很简单，对每条消费回复消息会检查correlationId是否匹配，是就回复到BlockingQueue。
		与此同时，主线程正在等待从BlockingQueue获取响应。最后，我们将响应返回给用户。
		
		
