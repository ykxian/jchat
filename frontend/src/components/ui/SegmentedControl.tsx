interface SegmentedControlOption<T extends string> {
  label: string;
  value: T;
}

interface SegmentedControlProps<T extends string> {
  ariaLabel: string;
  onChange: (value: T) => void;
  options: SegmentedControlOption<T>[];
  value: T;
}

export function SegmentedControl<T extends string>({
  ariaLabel,
  onChange,
  options,
  value
}: SegmentedControlProps<T>) {
  return (
    <div aria-label={ariaLabel} className="segmented-control" role="radiogroup">
      {options.map((option) => {
        const isActive = option.value === value;

        return (
          <button
            aria-checked={isActive}
            className={isActive ? "segmented-control__button segmented-control__button--active" : "segmented-control__button"}
            key={option.value}
            onClick={() => onChange(option.value)}
            role="radio"
            type="button"
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
