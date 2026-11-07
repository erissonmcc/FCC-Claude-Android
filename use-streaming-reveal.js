const React = require('react');
const {createStreamingReveal} = require('./streaming-reveal');

function useStreamingReveal(text, active) {
  const controller = React.useRef(null);
  if (!controller.current) controller.current = createStreamingReveal();
  const [result, setResult] = React.useState({text: active ? '' : text, streaming: active});

  React.useEffect(() => {
    controller.current.update(text, active, Date.now());
    setResult(controller.current.tick(Date.now()));
  }, [text, active]);

  React.useEffect(() => {
    const timer = setInterval(() => {
      const next = controller.current.tick(Date.now());
      setResult(previous => previous.text === next.text && previous.streaming === next.streaming ? previous : next);
    }, 16);
    return () => clearInterval(timer);
  }, []);

  if (!text.startsWith(result.text)) return {text: active ? '' : text, streaming: active};
  return result;
}

module.exports = {useStreamingReveal};
