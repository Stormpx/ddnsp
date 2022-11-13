package router;

import io.crowds.proxy.routing.CachedRouter;
import io.crowds.proxy.routing.Router;

import java.util.List;

public class CachedRouterTest extends RouterTest{
    @Override
    protected Router setupRouter(List<String> rules) {
        return new CachedRouter(rules,2,6,6);
    }
}
