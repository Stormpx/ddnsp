package router;

import io.crowds.proxy.routing.LinearRouter;
import io.crowds.proxy.routing.Router;

import java.util.List;

public class LinearRouterTest extends RouterTest{
    @Override
    protected Router setupRouter(List<String> rules) {
        return new LinearRouter(rules);
    }
}
