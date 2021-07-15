package lab10.rmi;

import java.rmi.RemoteException;

public class HelloServiceImpl implements HelloService {

    private MyClass2 obj2;
        
    public HelloServiceImpl() {}

    public String sayHello() {
    		return "Hello, world!";
    }
    
    public String sayHello(int n) {
    	    return "Hello, world! ==> " + n;
    }

    public /* synchronized */ void sayHello(Message m) {
    		System.out.println("hello: "+m.getContent());
    		 /*while (true) {}*/
    }

    public String sayHello(MyClass1 obj) throws RemoteException {
		obj.update(obj.get() + 1);
    return "Hello, world! ==> " + obj.get();
}
    
    public String sayHello(MyClass2 obj) throws RemoteException {
            obj2 = obj;
    		obj.update(obj.get() + 1);
    		return "Hello, world! ==> " + obj.get();
    }

    public String update() throws RemoteException {
        obj2.update(obj2.get() + 1);
        return "Update! ==> " + obj2.get();
    }
        
}